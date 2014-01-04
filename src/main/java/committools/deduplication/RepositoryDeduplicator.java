/**
 *
 */
package committools.deduplication;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import committools.data.GitCommitUtils;

/**
 * Deduplciate a folder of git repositories.
 * 
 * @author Miltiadis Allamanis
 * 
 */
public class RepositoryDeduplicator {

	/**
	 * Find duplicate projects and print them out. Since we need to compare all
	 * vs. all we need O(n^2) comparisons...
	 * 
	 * @param project_commits
	 * @param threshold
	 */
	public static void findDuplicates(
			Map<String, Set<ObjectId>> projectCommits, int threshold) {
		Set<String> projects = Sets.newTreeSet();
		projects.addAll(projectCommits.keySet());

		for (final String project1 : projectCommits.keySet()) {
			// Remove since we don't want to see it again
			projects.remove(project1);
			for (final String project2 : projects) {
				if (project1.equals(project2))
					continue;
				Set<ObjectId> project1Commits = projectCommits.get(project1);
				Set<ObjectId> project2Commits = projectCommits.get(project2);
				Set<ObjectId> commonCommits = Sets.newTreeSet();
				commonCommits.addAll(project1Commits);
				commonCommits.retainAll(project2Commits);
				if (commonCommits.size() > threshold) {
					System.out.println("Duplicate " + project1 + " with "
							+ project2);
				}
			}
		}
	}

	/**
	 * Returns all the folders in the given directory.
	 * 
	 * @param directory
	 *            the path to the directory where all projects exists.
	 * @return a vector with the all the repository directories.
	 * @throws Exception
	 *             when the folder is not found.
	 */
	public static List<String> getAllFoldersIn(final String directory)
			throws Exception {
		List<String> folders = Lists.newArrayList();
		File baseDir = new File(directory);
		if (!baseDir.isDirectory()) {
			throw new Exception(directory + " is not a directory");
		}
		File[] dirContents = baseDir.listFiles();
		for (int i = 0; i < dirContents.length; ++i) {
			if (dirContents[i].isDirectory()) {
				folders.add(dirContents[i].getAbsolutePath());
			}
		}
		return folders;
	}

	/**
	 * Get all commits for all projects.
	 * 
	 * @param directory
	 *            the directory to search at.
	 * @return a map from project (directory) to commits.
	 * @throws Exception
	 *             when the directory is not found.
	 */
	public static Map<String, Set<ObjectId>> getCommitsForAllProjects(
			String directory) throws Exception {
		Map<String, Set<ObjectId>> commitsDB = Maps.newTreeMap();
		List<String> repositories = getAllFoldersIn(directory);
		for (final String repository : repositories) {
			try {
				List<ObjectId> commitSet = GitCommitUtils
						.getCommits(repository);
				commitsDB.put(repository, new HashSet<ObjectId>(commitSet));
			} catch (Exception e) {
				System.err.println("Failed to get commits at " + repository
						+ ". Error: " + e.getMessage());
			}
		}
		return commitsDB;
	}

	/**
	 * Main entry point of code. To be used with command line: e.g. java -jar
	 * deduplicateProjects.jar /media/Documents/java_dump 10
	 * 
	 * @param args
	 *            args[0] is the path to the folder containing the projects,
	 *            args[1] is the minimum number of common commits to consider
	 *            that to projects are duplicates.
	 * @throws Exception
	 *             when the folder is not found.
	 */
	public static void main(final String[] args) throws Exception {
		if (args.length < 2) {
			System.err
					.println("Usage executable <path_to_project_folder> <common_commit_count_threshold>");
			return;
		}
		String pathToProject = args[0];
		int threshold = Integer.parseInt(args[1]);
		System.out.println("Searching for duplicates in folder "
				+ pathToProject + " having at least " + threshold
				+ " common commits.");
		Map<String, Set<ObjectId>> cmts = getCommitsForAllProjects(pathToProject);
		findDuplicates(cmts, threshold);
	}
}