package committools.data;

import java.io.Serializable;

import org.eclipse.jgit.lib.PersonIdent;

import com.google.common.base.Objects;

/**
 * A struct object containing the git commiter identity. Such objects are equal
 * when at least the name or the email are equal, allowing better deduplication.
 * 
 * @author Miltos Allamanis <m.allamanis@ed.ac.uk>
 * 
 */
public final class GitCommiterIdentity implements Serializable {

	private static final long serialVersionUID = -3880389858550759188L;

	public final String name;

	public final String emailUsername;

	public GitCommiterIdentity(final PersonIdent jgitIdenity) {
		name = jgitIdenity.getName();
		emailUsername = jgitIdenity.getEmailAddress().split("@")[0];
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final GitCommiterIdentity other = (GitCommiterIdentity) obj;
		return Objects.equal(name, other.name)
				|| Objects.equal(emailUsername, other.emailUsername);
	}

	@Override
	public int hashCode() {
		return 0;
	}

	@Override
	public String toString() {
		return name + ":" + emailUsername;
	}

}