/**
 * 
 */
package committools.deduplication;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.jgit.lib.ObjectId;

import codemining.util.parallel.ParallelThreadPool;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import committools.data.GitCommitUtils;

/**
 * A parallel deduplicator.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class ParallelDeduplicator {

	public class DuplicatesCheckerRunnable implements Runnable {

		final String baseProject;
		final List<String> remainingProjects;

		public DuplicatesCheckerRunnable(String base, List<String> remaining) {
			baseProject = base;
			remainingProjects = remaining;
		}

		@Override
		public void run() {
			try {
				final List<ObjectId> baseCommits = GitCommitUtils
						.getCommits(baseProject);

				for (final String project : remainingProjects) {
					Set<ObjectId> commonCommits = Sets.newTreeSet();
					commonCommits.addAll(baseCommits);
					try {
						commonCommits.retainAll(GitCommitUtils
								.getCommits(project));
						if (commonCommits.size() > threshold) {
							synchronized (duplicateProjects) {
								if (duplicateProjects.containsKey(baseProject)) {
									duplicateProjects.get(baseProject).add(
											project);
								} else {
									List<String> dProjects = Lists
											.newArrayList();
									dProjects.add(project);
									duplicateProjects.put(baseProject,
											dProjects);
								}
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}

				}
			} catch (Exception e) {
				return;
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
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.err.println("Usage <threshold> <directory>");
			return;
		}

		ParallelDeduplicator d = new ParallelDeduplicator(
				Integer.parseInt(args[0]));
		d.findAndPrintDuplicatesIn(args[1]);
	}

	protected Map<String, List<String>> duplicateProjects = Maps.newTreeMap();

	protected final int threshold;

	ParallelDeduplicator(int cThreshold) {
		threshold = cThreshold;
	}

	public void findAndPrintDuplicatesIn(String directory) throws Exception {
		List<String> projects = getAllFoldersIn(directory);
		Map<String, List<String>> compares = getAllProjectsToCompare(projects);

		final ParallelThreadPool threadPool = new ParallelThreadPool();
		// Add to executor pool

		for (final Entry<String, List<String>> entry : compares.entrySet()) {
			threadPool.pushTask(new DuplicatesCheckerRunnable(entry.getKey(),
					entry.getValue()));
		}

		// wait
		threadPool.waitForTermination();

		// print
		for (final Entry<String, List<String>> entry : duplicateProjects
				.entrySet()) {
			for (final String otherProject : entry.getValue()) {
				System.out.println(entry.getKey() + " " + otherProject);
			}
		}
	}

	public Map<String, List<String>> getAllProjectsToCompare(
			List<String> projects) {
		final Map<String, List<String>> combinations = new TreeMap<String, List<String>>();

		final List<String> projectsNotSeen = Lists.newArrayList();
		projectsNotSeen.addAll(projects);
		final long projectCount = projectsNotSeen.size();

		for (int i = 0; i < projectCount; i++) {
			final String currentProject = projectsNotSeen.get(0);
			projectsNotSeen.remove(currentProject);

			List<String> remainingProjects = Lists.newArrayList();
			remainingProjects.addAll(projectsNotSeen);
			combinations.put(currentProject, remainingProjects);
		}

		return combinations;
	}

}
