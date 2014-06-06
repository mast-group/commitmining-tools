/**
 * 
 */
package committools.dataextractors;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.MyersDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import committools.data.AbstractCommitWalker;

/**
 * Walk through the EditLists of a repository. Merge renamings if possible.
 * 
 * For historical reasons the default constructor creates a walker that only
 * looks at java file.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public abstract class EditListWalker extends AbstractCommitWalker {

	final DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);

	private final IOFileFilter editListFileFilter;

	private final RenameDetector renameDetector;

	@Deprecated
	public EditListWalker(final String repositoryDirectory) throws IOException {
		super(repositoryDirectory, AbstractCommitWalker.TOPOLOGICAL_WALK);
		df.setRepository(repository.getRepository());
		df.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
		df.setDetectRenames(true);
		renameDetector = new RenameDetector(repository.getRepository());
		editListFileFilter = new SuffixFileFilter(".java");
	}

	public EditListWalker(final String repositoryDirectory,
			final IOFileFilter fileFilter) throws IOException {
		super(repositoryDirectory, AbstractCommitWalker.TOPOLOGICAL_WALK);
		df.setRepository(repository.getRepository());
		df.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
		renameDetector = new RenameDetector(repository.getRepository());
		df.setDetectRenames(true);
		editListFileFilter = fileFilter;
	}

	/**
	 * @param parentOid
	 * @return
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws IOException
	 */
	private byte[] getBytesForObject(final ObjectId parentOid)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		final byte[] parentObj;
		if (!parentOid.equals(ObjectId.zeroId())) {
			parentObj = repository.getRepository()
					.open(parentOid, org.eclipse.jgit.lib.Constants.OBJ_BLOB)
					.getCachedBytes();
		} else {
			parentObj = new byte[0];
		}
		return parentObj;
	}

	/**
	 * @param currentObj
	 * @param parentObj
	 * @return
	 */
	public EditList getDiff(final byte[] currentObj, final byte[] parentObj) {
		final EditList el = MyersDiff.INSTANCE.diff(
				RawTextComparator.WS_IGNORE_ALL,
				parentObj.length > 0 ? new RawText(parentObj)
						: RawText.EMPTY_TEXT,
				currentObj.length > 0 ? new RawText(currentObj)
						: RawText.EMPTY_TEXT);
		return el;
	}

	public EditList getEditList(final DiffEntry entry)
			throws LargeObjectException, MissingObjectException,
			IncorrectObjectTypeException, IOException {
		final ObjectId baseOid = entry.getNewId().toObjectId();
		final byte[] currentObj = getBytesForObject(baseOid);

		final ObjectId parentOid = entry.getOldId().toObjectId();
		final byte[] parentObj = getBytesForObject(parentOid);

		if (RawText.isBinary(currentObj) || RawText.isBinary(parentObj)) {
			return new EditList();
		}

		return getDiff(currentObj, parentObj);
	}

	private AbstractTreeIterator getTreeIterator(final String name)
			throws IOException {
		final ObjectId id = repository.getRepository().resolve(name);
		if (id == null) {
			throw new IllegalArgumentException(name);
		}
		final CanonicalTreeParser p = new CanonicalTreeParser();
		final ObjectReader or = repository.getRepository().newObjectReader();
		try {
			p.reset(or, new RevWalk(repository.getRepository()).parseTree(id));
			return p;
		} finally {
			or.release();
		}
	}

	/**
	 * @param entry
	 * @param el
	 * @param commit
	 * @throws IOException
	 */
	public abstract void visitDiffEntry(final DiffEntry entry,
			final EditList el, final RevCommit commit) throws IOException;

	@Override
	public final boolean vistCommit(final RevCommit commit) {
		try {
			final RevCommit[] parents = commit.getParents();
			if (parents.length == 1) { // TODO Forget merges?
				final RevCommit parent = parents[0];

				final List<DiffEntry> diffs = repository.diff()
						.setNewTree(getTreeIterator(commit.name()))
						.setOldTree(getTreeIterator(parent.name())).call();

				renameDetector.reset();
				renameDetector.addAll(diffs);

				for (final DiffEntry entry : renameDetector.compute()) {
					if (!editListFileFilter
							.accept(new File(entry.getNewPath()))
							&& !editListFileFilter.accept(new File(entry
									.getOldPath()))) {
						continue;
					}

					final EditList el = getEditList(entry);

					visitDiffEntry(entry, el, commit);
				}

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
