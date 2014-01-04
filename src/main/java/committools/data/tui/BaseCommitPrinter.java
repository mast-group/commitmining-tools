package committools.data.tui;

import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

import committools.data.GitCommitUtils;

/**
 * Print the base commits.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class BaseCommitPrinter {

	public static void main(final String[] args) throws IOException {
		if (args.length != 1) {
			System.err.println("Usage <repositoryDirectory>");
			System.exit(-1);
		}
		final Git git = GitCommitUtils.getGitRepository(args[0]);
		final List<RevCommit> allCommits = GitCommitUtils.getAllBaseCommits(git);
		for (final RevCommit commit : allCommits) {
			System.out.println(commit);
		}
	}

}
