/**
 *
 */
package committools.data;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;

import com.google.common.collect.Lists;

/**
 * Print the number of commits that contain a set of keywords.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class CommitLogMatches {

	/**
	 * @param args
	 * @throws GitAPIException
	 * @throws IOException
	 * @throws NoHeadException
	 */
	public static void main(String[] args) throws NoHeadException, IOException,
			GitAPIException {
		if (args.length < 3) {
			System.err
			.println("Usage single|multiple <directory> <keywords...>");
			System.exit(-1);
		}
		File directory = new File(args[1]);
		checkArgument(directory.isDirectory());

		List<String> keywords = Lists.newArrayList();
		for (int i = 2; i < args.length; i++) {
			keywords.add(args[i]);
		}

		if (args[0].equals("single")) {
			System.out.println(numCommits(directory, keywords));
		} else if (args[0].equals("multiple")) {
			for (final File project : directory.listFiles()) {
				try {
					System.out.println(project.getName() + ","
							+ numCommits(project, keywords));
				} catch (Throwable e) {
					LOGGER.warning("Failed to get count for project " + project
							+ " because " + ExceptionUtils.getFullStackTrace(e));
				}
			}
		} else {
			throw new IllegalArgumentException("Unrecognized " + args[0]);
		}

	}

	public static int numCommits(final File directory,
			final List<String> keywords) throws NoHeadException, IOException,
			GitAPIException {
		List<String> allCommitMessages = GitCommitUtils
				.getAllCommitMessages(directory.getAbsolutePath());
		int matchingCommits = 0;
		for (final String message : allCommitMessages) {
			boolean matches = false;
			for (final String keyword : keywords) {
				if (message.contains(keyword)) {
					matches = true;
					break;
				}
			}
			matchingCommits += matches ? 1 : 0;
		}
		return matchingCommits;
	}

	private static final Logger LOGGER = Logger
			.getLogger(CommitLogMatches.class.getName());

	private CommitLogMatches() {
		// Cannot be constructed.
	}

}
