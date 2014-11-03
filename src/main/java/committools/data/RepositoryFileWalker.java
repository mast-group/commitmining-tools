/**
 *
 */
package committools.data;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.CannotDeleteCurrentBranchException;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.NotMergedException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * A special commit walker that visits the commits one-by-one, changing the
 * working tree (files) on the fly.
 *
 * This class handles Git checkouts gracefully and terminating its thread is
 * delayed until the repository is restored to the initial state.
 *
 * Override visitCommitFiles to implement a visitor. When the visitor is called
 * a the state of the repositoryDir is at the given commit. Optionally, override
 * isVisitableCommit to specify which commits will be visited.
 *
 *
 * Note that JGit does not work well with symlinks. This means that exceptions
 * with checkout of various versions may be thrown. In case this happens, you
 * need to set to the given repository in .git/config in the [core] settings
 *
 * symlink = false
 *
 * then you need to make sure that all symlinks are removed. You may do that, by
 * checking out a new branch, setting it to a very old (e.g. the first) commit
 * and then checking out the master branch.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public abstract class RepositoryFileWalker extends AbstractCommitWalker {

	/**
	 * Handle a TERM signal by gracefully allowing the walker to exit.
	 *
	 */
	public class TermHandler extends Thread {
		@Override
		public void run() {
			LOGGER.warning("Shuting down gracefully, please wait...");
			terminateLock.lock();
			LOGGER.warning("Lock acquired in shutdown handler.");
			terminating = true;
			try {
				// Artificially pause to let the commit walker to finish
				// TODO: Detect when we're done.
				Thread.sleep(5000);
			} catch (final InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public static final String TEMPORARY_BRANCH_NAME = "temporaryBranchUsedbyRepositoryFileWalker";

	private static final Logger LOGGER = Logger
			.getLogger(RepositoryFileWalker.class.getName());

	final protected File repositoryDir;

	final protected String mainBranchName;

	final Lock terminateLock = new ReentrantLock();
	volatile boolean terminating = false;

	public RepositoryFileWalker(final String repositoryDirectory,
			final ICommitWalkingStrategy walkingStrategy) throws IOException {
		super(repositoryDirectory, walkingStrategy);
		repositoryDir = new File(repositoryDirectory);
		mainBranchName = repository.getRepository().getBranch();

	}

	/**
	 * Try deleting the temporary branch.
	 */
	private void deleteTestBranchIfExists() {
		try {
			switchToMainAndDeleteFrom(TEMPORARY_BRANCH_NAME);
		} catch (final Exception e) {
			LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
		}
	}

	@Override
	public void doWalk() {
		final TermHandler termSignalHandler = new TermHandler();
		Runtime.getRuntime().addShutdownHook(termSignalHandler);
		super.doWalk();
		if (!terminating) { // if we are not already shutting down
			Runtime.getRuntime().removeShutdownHook(termSignalHandler);
		}
	}

	@Override
	public void doWalk(final int iterationLimit) {
		final TermHandler termSignalHandler = new TermHandler();
		Runtime.getRuntime().addShutdownHook(termSignalHandler);
		super.doWalk(iterationLimit);
		if (!terminating) { // if we are not already shutting down
			Runtime.getRuntime().removeShutdownHook(termSignalHandler);
		}
	}

	/**
	 * Returns true if the given commit will be visited. Override this method to
	 * specify which commits will be visited.
	 *
	 * @param commit
	 * @return
	 */
	public boolean isVisitableCommit(final RevCommit commit) {
		return true;
	}

	/**
	 * Switch to the main branch and delete the temporary branch.
	 *
	 * @throws GitAPIException
	 * @throws RefAlreadyExistsException
	 * @throws RefNotFoundException
	 * @throws InvalidRefNameException
	 * @throws CheckoutConflictException
	 * @throws NotMergedException
	 * @throws CannotDeleteCurrentBranchException
	 */
	private void switchToMainAndDeleteFrom(final String tempBranch)
			throws GitAPIException, RefAlreadyExistsException,
			RefNotFoundException, InvalidRefNameException,
			CheckoutConflictException, NotMergedException,
			CannotDeleteCurrentBranchException {
		try {
			repository.reset().setMode(ResetType.HARD).call();
		} finally {
			try {
				repository.checkout().setCreateBranch(false)
				.setName(mainBranchName).setForce(true).call();
			} finally {
				try {
					repository.reset().setMode(ResetType.HARD).call();
				} finally {
					repository.branchDelete().setForce(true)
					.setBranchNames(tempBranch).call();
				}
			}
		}
	}

	/**
	 * Visit the specified commit. When the function is called the working tree
	 * (files in the directory) will have been changed according to that commit.
	 *
	 * @param commit
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws IOException
	 */
	public abstract void visitCommitFiles(final RevCommit commit);

	@Override
	public boolean vistCommit(final RevCommit commit) {
		if (!terminateLock.tryLock()) {
			LOGGER.warning("Failed to aquire lock. Stoping tree walk...");
			return false;
		}

		try {
			if (isVisitableCommit(commit)) {
				deleteTestBranchIfExists();
				repository.checkout().setCreateBranch(true)
						.setName(TEMPORARY_BRANCH_NAME).setStartPoint(commit)
				.setForce(true).call();
				try {
					visitCommitFiles(commit);
				} finally {
					switchToMainAndDeleteFrom(TEMPORARY_BRANCH_NAME);
				}
			}
		} catch (final Throwable e) {
			LOGGER.warning(ExceptionUtils.getFullStackTrace(e));
		}

		terminateLock.unlock();
		try {
			// Allow thread to pause, allowing the shutdown
			// thread to lock the mutex, if needed.
			Thread.sleep(1);
		} catch (final InterruptedException e) {
		}
		return !terminating;
	}

	@Override
	public void walkCompleted() {
		deleteTestBranchIfExists();
	}
}
