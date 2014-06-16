/**
 * 
 */
package committools.dataextractors;

import java.io.IOException;

import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.revwalk.RevCommit;

import committools.data.AbstractCommitWalker;
import committools.data.EditListRetriever;
import committools.data.EditListRetriever.IEditListCallback;

/**
 * Walk through the EditLists of a repository. Merge renamings if possible.
 * 
 * For historical reasons the default constructor creates a walker that only
 * looks at java file.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public abstract class EditListWalker extends AbstractCommitWalker implements
		IEditListCallback {

	private final EditListRetriever diffRetriver;

	@Deprecated
	public EditListWalker(final String repositoryDirectory) throws IOException {
		super(repositoryDirectory, AbstractCommitWalker.TOPOLOGICAL_WALK);
		diffRetriver = new EditListRetriever(repository, new SuffixFileFilter(
				".java"));
	}

	public EditListWalker(final String repositoryDirectory,
			final IOFileFilter fileFilter) throws IOException {
		super(repositoryDirectory, AbstractCommitWalker.TOPOLOGICAL_WALK);
		diffRetriver = new EditListRetriever(repository, fileFilter);
	}

	/**
	 * @param entry
	 * @param el
	 * @param commit
	 * @throws IOException
	 */
	@Override
	public abstract void visitDiffEntry(final DiffEntry entry,
			final EditList el, final RevCommit commit) throws IOException;

	@Override
	public final boolean vistCommit(final RevCommit commit) {
		try {
			final RevCommit[] parents = commit.getParents();
			if (parents.length == 1) { // TODO Forget merges?
				final RevCommit parent = parents[0];
				diffRetriver.retrieveEditListBetweenAndCallback(commit, parent,
						this);
			}

		} catch (final Exception e) {
			System.err.println(e);
		}
		return true;
	}

	@Override
	public void walkCompleted() {
		// Nothing here. May be overriden.
	}

}
