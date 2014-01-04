/**
 * 
 */
package committools.data;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Retrieve all commits for a given repository. Static utility class.
 * 
 * @author Miltos Allamanis <m.allamanis@sms.ed.ac.uk>
 * 
 */
public final class GitCommitUtils {

	/**
	 * Return a list of all the commits in the main branches. Start from HEAD
	 * and then go back, always choosing the first parent commit, then reverse
	 * list.
	 * 
	 * @param git
	 * @return
	 * @throws IOException
	 * @throws NoWorkTreeException
	 */
	public static List<RevCommit> getAllBaseCommits(final Git git)
			throws NoWorkTreeException, IOException {
		final List<RevCommit> commitList = Lists.newArrayList();

		final AnyObjectId headId = git.getRepository().resolve(Constants.HEAD);

		final RevWalk walk = new RevWalk(git.getRepository());
		RevCommit currentCommit = walk.parseCommit(checkNotNull(headId));

		commitList.add(checkNotNull(currentCommit));
		while (currentCommit.getParents().length > 0) {
			currentCommit = walk.parseCommit(currentCommit.getParent(0)
					.toObjectId());
			commitList.add(checkNotNull(currentCommit));
		}

		Collections.reverse(commitList);
		return commitList;
	}

	/**
	 * Return a list of all commit messages.
	 * 
	 * @param repositoryDir
	 * @return
	 * @throws IOException
	 * @throws NoHeadException
	 * @throws GitAPIException
	 */
	public static List<String> getAllCommitMessages(final String repositoryDir)
			throws IOException, NoHeadException, GitAPIException {
		final List<String> commitSet = Lists.newArrayList();
		final Git git = getGitRepository(repositoryDir);

		final RevWalk walk = new RevWalk(git.getRepository());

		final Iterable<RevCommit> logs = git.log().call();
		final Iterator<RevCommit> i = logs.iterator();

		while (i.hasNext()) {
			final RevCommit commit = walk.parseCommit(i.next());
			commitSet.add(commit.getFullMessage());
		}

		return commitSet;
	}

	/**
	 * Return all the commits in topological order, starting from the first
	 * commit.
	 * 
	 * @param git
	 * @return
	 * @throws NoWorkTreeException
	 * @throws IOException
	 * @throws NoHeadException
	 * @throws GitAPIException
	 */
	public static List<RevCommit> getAllCommitsTopological(final Git git)
			throws NoWorkTreeException, IOException, NoHeadException,
			GitAPIException {
		if (!git.log().call().iterator().hasNext()) {
			return Collections.emptyList();
		}

		final List<RevCommit> commitList = Lists.newArrayList();

		final RevWalk rw = new RevWalk(git.getRepository());
		final AnyObjectId headId = git.getRepository().resolve(Constants.HEAD);

		final RevCommit head = rw.parseCommit(headId);
		rw.sort(RevSort.REVERSE, true);
		rw.sort(RevSort.TOPO, true);
		rw.markStart(head);
		commitList.add(head);

		RevCommit currentCommit = rw.next();
		while (currentCommit != null) {
			commitList.add(currentCommit);
			currentCommit = rw.next();
		}

		return commitList;
	}

	/**
	 * Get all the commits of a repository in a set.
	 * 
	 * @param repositoryDir
	 *            the directory of the repository. Should not have trailing
	 *            slash.
	 * @return
	 * @throws IOException
	 * @throws GitAPIException
	 * @throws NoHeadException
	 */
	public static List<ObjectId> getCommits(final String repositoryDir)
			throws IOException, NoHeadException, GitAPIException {
		final List<ObjectId> commitSet = Lists.newArrayList();
		final Git git = getGitRepository(repositoryDir);

		final RevWalk walk = new RevWalk(git.getRepository());

		final Iterable<RevCommit> logs = git.log().call();
		final Iterator<RevCommit> i = logs.iterator();

		while (i.hasNext()) {
			final RevCommit commit = walk.parseCommit(i.next());
			commitSet.add(commit.getId());
		}

		return commitSet;
	}

	/**
	 * Return all the commits given the time.
	 * 
	 * @param git
	 * @return
	 * @throws IOException
	 * @throws NoHeadException
	 * @throws GitAPIException
	 */
	public static SortedMap<Integer, RevCommit> getCommitsWithTime(final Git git)
			throws IOException, NoHeadException, GitAPIException {
		final SortedMap<Integer, RevCommit> commitsInTime = Maps.newTreeMap();

		final RevWalk walk = new RevWalk(git.getRepository());

		final Iterable<RevCommit> logs = git.log().call();
		final Iterator<RevCommit> i = logs.iterator();

		while (i.hasNext()) {
			final RevCommit commit = walk.parseCommit(i.next());
			commitsInTime.put(commit.getCommitTime(), commit);
		}

		return commitsInTime;
	}

	/**
	 * @param repositoryDir
	 * @return
	 * @throws IOException
	 */
	public static Git getGitRepository(final String repositoryDir)
			throws IOException {
		// Open a single repository
		final FileRepositoryBuilder builder = new FileRepositoryBuilder();
		final Repository repository = builder
				.setGitDir(new File(repositoryDir + "/.git")).readEnvironment()
				.findGitDir().build();
		final Git git = new Git(repository);
		return git;
	}

	private GitCommitUtils() {
		// No instantiation
	}
}
