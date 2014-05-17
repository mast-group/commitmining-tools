/**
 * 
 */
package committools.dataextractors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.blame.BlameGenerator;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.MyersDiff;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;

import com.google.common.collect.Maps;
import committools.data.AbstractCommitWalker;
import committools.data.RepositoryFileWalker;

/**
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public class LineLifecycle {

	private class BlameRetriever extends RepositoryFileWalker {
		public BlameRetriever(final String repositoryDirectory)
				throws IOException {
			super(repositoryDirectory, AbstractCommitWalker.TOPOLOGICAL_WALK);
		}

		public void getBlameForLines(final String file, final int lineFrom,
				final int lineTo, final int changedTs) throws IOException {
			final BlameGenerator bg = new BlameGenerator(
					repository.getRepository(), file);
			bg.setDiffAlgorithm(MyersDiff.INSTANCE);
			bg.setTextComparator(RawTextComparator.WS_IGNORE_ALL);

			bg.push(null, repository.getRepository().resolve(Constants.HEAD));
			final BlameResult br = bg.computeBlameResult();
			br.computeRange(lineFrom, lineTo);

			for (int i = lineFrom; i < lineTo; i++) {
				final int timeDiff = changedTs
						- br.getSourceCommit(i).getCommitTime();
				System.out.println(timeDiff);
			}

			// Now store the stuff in before/after and num lines(?).
			// Maybe we need to normalize over a) all the lines b) all the lines
			// that ever changed? c) something else
			// God help us
		}

		@Override
		public void visitCommitFiles(final RevCommit commit) {
			if (!diffs.containsKey(commit.name())) {
				return;
			}

			final List<DiffEdits> edits = diffs.get(commit.name());
			for (final DiffEdits entry : edits) {
				final EditList el = entry.edits;
				for (final Edit edit : el) {
					try {
						if (edit.getType() == Edit.Type.DELETE
								|| edit.getType() == Edit.Type.REPLACE) {
							/*
							 * System.out.println("Trying for " + edit + " at "
							 * + entry.entry.getOldPath() + " at commit " +
							 * commit.name());
							 */
							getBlameForLines(entry.entry.getOldPath(),
									edit.getBeginA(), edit.getEndA(),
									entry.timestamp);
						}
					} catch (final IOException e) {
						System.err.println(e.getMessage());
					}
				}
			}
		}
	}

	public static class DiffEdits {

		public final DiffEntry entry;

		public final EditList edits;
		public final int timestamp;

		public DiffEdits(final DiffEntry ent, final EditList ed, final int ts) {
			entry = ent;
			edits = ed;
			timestamp = ts;
		}
	}

	private class EditListRetriever extends EditListWalker {
		public EditListRetriever(final String repositoryDirectory)
				throws IOException {
			super(repositoryDirectory);
		}

		@Override
		public void visitDiffEntry(final DiffEntry entry, final EditList el,
				final RevCommit commit) throws IOException {

			final String commitId = commit.getParent(0).name(); // Assign edit
																// to the
																// previous
																// commit.
			if (!diffs.containsKey(commitId)) {
				diffs.put(commitId, new ArrayList<DiffEdits>());
			}

			final List<DiffEdits> edits = diffs.get(commitId);
			edits.add(new DiffEdits(entry, el, commit.getCommitTime()));
		}
	}

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(final String[] args) throws IOException {
		if (args.length < 1) {
			System.err.println("Usage <repositoryDir>");
			System.exit(-1);
		}
		final LineLifecycle lc = new LineLifecycle(args[0]);
		lc.calculateLifecycle();
	}

	private final String repositoryDir;

	private final Map<String, List<DiffEdits>> diffs = Maps.newTreeMap();

	public LineLifecycle(final String repositoryDirectory) throws IOException {
		repositoryDir = repositoryDirectory;
	}

	public void calculateLifecycle() throws IOException {
		final EditListRetriever el = new EditListRetriever(repositoryDir);
		el.doWalk();
		// System.out.println(diffs);
		final BlameRetriever br = new BlameRetriever(repositoryDir);
		br.doWalk();
	}

	public void calculateLifecycle(final int commitN) throws IOException {
		final EditListRetriever el = new EditListRetriever(repositoryDir);
		el.doWalk(commitN);
		// System.out.println(diffs);
		final BlameRetriever br = new BlameRetriever(repositoryDir);
		br.doWalk(commitN);
	}
}
