package com.clustercontrol.notify.model;

import java.io.Serializable;
import java.util.Arrays;

import javax.persistence.*;

/**
 * The primary key class for the cc_notify_command_info database table.
 * 
 */
@Embeddable
public class NotifyCommandInfoEntityPK implements Serializable {
	//default serial version id, required for serializable classes.
	private static final long serialVersionUID = 1L;
	private String notifyId;

	public NotifyCommandInfoEntityPK() {
	}

	public NotifyCommandInfoEntityPK(String notifyId) {
		this.setNotifyId(notifyId);
	}

	@Column(name="notify_id")
	public String getNotifyId() {
		return this.notifyId;
	}
	public void setNotifyId(String notifyId) {
		this.notifyId = notifyId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof NotifyCommandInfoEntityPK)) {
			return false;
		}
		NotifyCommandInfoEntityPK castOther = (NotifyCommandInfoEntityPK)other;
		return
				this.notifyId.equals(castOther.notifyId);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int hash = 17;
		hash = hash * prime + this.notifyId.hashCode();

		return hash;
	}

	@Override
	public String toString() {
		String[] names = {
				"notifyId"
		};
		String[] values = {
				this.notifyId
		};
		return Arrays.toString(names) + " = " + Arrays.toString(values);
	}
}