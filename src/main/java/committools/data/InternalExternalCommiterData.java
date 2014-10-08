/**
 *
 */
package committools.data;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.logging.Logger;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.revwalk.RevCommit;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;

/**
 * Get the number of internal, external users in a repository and the commiter
 * retention statistics for the internal users.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class InternalExternalCommiterData {

	/**
	 * @param args
	 */
	public static void main(final String[] args) {
		if (args.length != 1) {
			System.err.println("Usage <directoryOfRepos>");
			System.exit(-1);
		}
		final File projectsDir = new File(args[0]);
		checkArgument(projectsDir.isDirectory());
		System.out.println("project,internal,external,internalRetention");
		for (final File project : projectsDir.listFiles()) {
			try {
				final InternalExternalCommiterData iecd = new InternalExternalCommiterData();
				iecd.buildData(project.getAbsolutePath());
				System.out.println(project.getName() + ","
						+ iecd.getNumInternal() + "," + iecd.getNumExternal()
						+ ","
						+ String.format("%.4f", iecd.getInternalRetention()));
			} catch (final Throwable e) {
				LOGGER.warning("Failed to extract information for " + project
						+ " because " + ExceptionUtils.getFullStackTrace(e));
			}
		}

	}

	private static final Logger LOGGER = Logger
			.getLogger(InternalExternalCommiterData.class.getName());

	/**
	 * The number of commits a user needs to have to be considered, internal.
	 */
	public static final int INTERNAL_COMMITER_LIMIT = 20;

	public final Set<GitCommiterIdentity> internalCommiters = Sets.newHashSet();

	public final Set<GitCommiterIdentity> externalCommiters = Sets.newHashSet();
	private double weightedInternalCommiterRetention = 0;

	private void buildData(final String absolutePath) throws NoHeadException,
			IOException, GitAPIException {
		final SortedMap<Integer, RevCommit> commitsWithTime = GitCommitUtils
				.getCommitsWithTime(GitCommitUtils
						.getGitRepository(absolutePath));
		final Multiset<GitCommiterIdentity> numCommits = HashMultiset.create();
		final Map<GitCommiterIdentity, Integer> startTimestamp = Maps
				.newHashMap();
		final Map<GitCommiterIdentity, Integer> lastTimestamp = Maps
				.newHashMap();

		for (final Entry<Integer, RevCommit> commit : commitsWithTime
				.entrySet()) {
			if (commit.getValue().getParentCount() > 1) {
				// Merge commit, ignore.
				continue;
			}
			final GitCommiterIdentity commiter = new GitCommiterIdentity(commit
					.getValue().getAuthorIdent());
			numCommits.add(commiter);
			final int currentCommitTime = commit.getValue().getCommitTime();
			if (!startTimestamp.containsKey(commiter)) {
				startTimestamp.put(commiter, currentCommitTime);
			}
			checkArgument(lastTimestamp.containsKey(commiter) ? lastTimestamp
					.get(commiter) < currentCommitTime : true);
			lastTimestamp.put(commiter, currentCommitTime);
		}

		for (final Multiset.Entry<GitCommiterIdentity> commiter : numCommits
				.entrySet()) {
			if (commiter.getCount() >= INTERNAL_COMMITER_LIMIT) {
				internalCommiters.add(commiter.getElement());
			} else {
				externalCommiters.add(commiter.getElement());
			}
		}

		double weightedTotalTime = 0;
		long sumWeights = 0;
		for (final GitCommiterIdentity internalCommiter : internalCommiters) {
			final int timeDiff = lastTimestamp.get(internalCommiter)
					- startTimestamp.get(internalCommiter);
			checkArgument(timeDiff > 0);
			final int nCommits = numCommits.count(internalCommiter);
			// Weight the number of days by number of commits.
			weightedTotalTime += nCommits * ((double) timeDiff) / 60. / 60.
					/ 24.;
			sumWeights += nCommits;
		}
		weightedInternalCommiterRetention = weightedTotalTime / sumWeights;
	}

	private double getInternalRetention() {
		return weightedInternalCommiterRetention;
	}

	private int getNumExternal() {
		return externalCommiters.size();
	}

	private int getNumInternal() {
		return internalCommiters.size();
	}

}
