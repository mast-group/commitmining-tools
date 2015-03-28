package committools.data;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
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
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import com.google.common.collect.Lists;

/**
 *
 * Given two commit objects, retrieve the diff and entry list between the two
 * commits. The class also detects renamings.
 *
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 *
 */
public class EditListRetriever {

	public interface IEditListCallback {
		public void visitDiffEntry(final DiffEntry entry,
				final EditList editList, final RevCommit commit)
				throws IOException;
	}

	/**
	 * Get the line change churn for the given edit list.
	 *
	 * @param editList
	 * @return
	 */
	public static long getChangeChurn(final EditList editList) {
		long lineChurn = 0;
		for (final Edit edit : editList) {
			final int nALines = edit.getEndA() - edit.getBeginA();
			final int nBLines = edit.getEndB() - edit.getBeginB();
			if (nALines > nBLines) {
				lineChurn += nALines;
			} else {
				lineChurn += nBLines;
			}
		}
		return lineChurn;
	}

	private static final Logger LOGGER = Logger
			.getLogger(EditListRetriever.class.getName());

	private final DiffFormatter df = new DiffFormatter(
			DisabledOutputStream.INSTANCE);

	private final IOFileFilter editListFileFilter;

	private final RenameDetector renameDetector;

	private final Git repository;

	/**
	 *
	 * @param repository
	 *            the git repository to use
	 * @param fileFilter
	 *            the files to present edit lists for
	 */
	public EditListRetriever(final Git repository, final IOFileFilter fileFilter) {
		this.repository = repository;
		df.setRepository(repository.getRepository());
		df.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
		renameDetector = new RenameDetector(repository.getRepository());
		df.setDetectRenames(true);
		editListFileFilter = fileFilter;
	}

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
	private EditList getDiff(final byte[] currentObj, final byte[] parentObj) {
		final EditList el = MyersDiff.INSTANCE.diff(
				RawTextComparator.WS_IGNORE_ALL,
				parentObj.length > 0 ? new RawText(parentObj)
						: RawText.EMPTY_TEXT,
				currentObj.length > 0 ? new RawText(currentObj)
						: RawText.EMPTY_TEXT);
		return el;
	}

	private EditList getEditList(final DiffEntry entry)
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

	public List<EditList> retrieveEditListBetween(final RevCommit to,
			final RevCommit from) throws GitAPIException, IOException,
			LargeObjectException, MissingObjectException,
			IncorrectObjectTypeException {
		final List<DiffEntry> diffs = repository.diff()
				.setNewTree(getTreeIterator(to.name()))
				.setOldTree(getTreeIterator(from.name())).call();

		renameDetector.reset();
		renameDetector.addAll(diffs);

		final List<EditList> edits = Lists.newArrayList();
		for (final DiffEntry entry : renameDetector.compute()) {
			if (!editListFileFilter.accept(new File(entry.getNewPath()))
					&& !editListFileFilter.accept(new File(entry.getOldPath()))) {
				continue;
			}

			final EditList el = getEditList(entry);

			edits.add(el);
		}
		return edits;
	}

	/**
	 * Retrieve the edit list between the from and the to commit.
	 *
	 * @param to 
	 * @param from the original revision or null to compare from an empty tree
	 * @throws GitAPIException
	 * @throws IOException
	 * @throws LargeObjectException
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 */
	public void retrieveEditListBetweenAndCallback(final RevCommit to,
			final RevCommit from, final IEditListCallback callback)
			throws GitAPIException, IOException, LargeObjectException,
			MissingObjectException, IncorrectObjectTypeException {
		final List<DiffEntry> diffs;
		if (from != null) {
			diffs= repository.diff()
				.setNewTree(getTreeIterator(to.name()))
				.setOldTree(getTreeIterator(from.name())).call();
		} else {
			diffs= repository.diff()
					.setNewTree(getTreeIterator(to.name()))
					.setOldTree(new EmptyTreeIterator()).call();
		}
		renameDetector.reset();
		renameDetector.addAll(diffs);

		for (final DiffEntry entry : renameDetector.compute()) {
			try {
				if (!editListFileFilter.accept(new File(entry.getNewPath()))
						&& !editListFileFilter.accept(new File(entry
								.getOldPath()))) {
					continue;
				}

				final EditList el = getEditList(entry);

				callback.visitDiffEntry(entry, el, to);
			} catch (final Throwable t) {
				LOGGER.warning("Failed fully executing callback for DiffEntry because "
						+ ExceptionUtils.getFullStackTrace(t));
			}
		}
	}
}