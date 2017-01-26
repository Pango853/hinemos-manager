/*

 Copyright (C) 2013 NTT DATA Corporation

 This program is free software; you can redistribute it and/or
 Modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation, version 2.

 This program is distributed in the hope that it will be
 useful, but WITHOUT ANY WARRANTY; without even the implied
 warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 PURPOSE.  See the GNU General Public License for more details.

 */
package com.clustercontrol.winevent.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.persistence.Cacheable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.clustercontrol.commons.util.HinemosEntityManager;
import com.clustercontrol.commons.util.JpaTransactionManager;


/**
 * The persistent class for the cc_monitor_winevent_id_info database table.
 * 
 */
@Entity
@Table(name="cc_monitor_winevent_id_info", schema="setting")
@Cacheable(true)
public class MonitorWinEventIdInfoEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	private MonitorWinEventIdInfoEntityPK id;
	private MonitorWinEventInfoEntity monitorWinEventInfoEntity;

	@Deprecated
	public MonitorWinEventIdInfoEntity() {
	}

	public MonitorWinEventIdInfoEntity(MonitorWinEventIdInfoEntityPK id,
			MonitorWinEventInfoEntity monitorWinEventInfoEntity) {
		this.setId(id);
		HinemosEntityManager em = new JpaTransactionManager().getEntityManager();
		em.persist(this);
		this.relateToMonitorWinEventInfoEntity(monitorWinEventInfoEntity);
	}

	@EmbeddedId
	public MonitorWinEventIdInfoEntityPK getId() {
		return this.id;
	}

	public void setId(MonitorWinEventIdInfoEntityPK id) {
		this.id = id;
	}

	//bi-directional many-to-one association to MonitorInfoEntity
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="monitor_id", insertable=false, updatable=false)
	public MonitorWinEventInfoEntity getMonitorWinEventInfoEntity() {
		return this.monitorWinEventInfoEntity;
	}

	@Deprecated
	public void setMonitorWinEventInfoEntity(MonitorWinEventInfoEntity monitorWinEventInfoEntity) {
		this.monitorWinEventInfoEntity = monitorWinEventInfoEntity;
	}

	/**
	 * MonitorInfoEntityオブジェクト参照設定<BR>
	 * 
	 * MonitorInfoEntity設定時はSetterに代わりこちらを使用すること。
	 * 
	 * JPAの仕様(JSR 220)では、データ更新に伴うrelationshipの管理はユーザに委ねられており、
	 * INSERTやDELETE時に、そのオブジェクトに対する参照をメンテナンスする処理を実装する。
	 * 
	 * JSR 220 3.2.3 Synchronization to the Database
	 * 
	 * Bidirectional relationships between managed entities will be persisted
	 * based on references held by the owning side of the relationship.
	 * It is the developer’s responsibility to keep the in-memory references
	 * held on the owning side and those held on the inverse side consistent
	 * with each other when they change.
	 */
	public void relateToMonitorWinEventInfoEntity(MonitorWinEventInfoEntity monitorWinEventInfoEntity) {
		this.setMonitorWinEventInfoEntity(monitorWinEventInfoEntity);
		if (monitorWinEventInfoEntity != null) {
			List<MonitorWinEventIdInfoEntity> list = monitorWinEventInfoEntity.getMonitorWinEventIdInfoEntities();
			if (list == null) {
				list = new ArrayList<MonitorWinEventIdInfoEntity>();
			} else {
				for(MonitorWinEventIdInfoEntity entity : list){
					if (entity.getId().getMonitorId().equals(this.getId().getMonitorId())) {
						return;
					}
				}
			}
			list.add(this);
			monitorWinEventInfoEntity.setMonitorWinEventIdInfoEntities(list);
		}
	}


	/**
	 * 削除前処理<BR>
	 * 
	 * JPAの仕様(JSR 220)では、データ更新に伴うrelationshipの管理はユーザに委ねられており、
	 * INSERTやDELETE時に、そのオブジェクトに対する参照をメンテナンスする処理を実装する。
	 * 
	 * JSR 220 3.2.3 Synchronization to the Database
	 * 
	 * Bidirectional relationships between managed entities will be persisted
	 * based on references held by the owning side of the relationship.
	 * It is the developer’s responsibility to keep the in-memory references
	 * held on the owning side and those held on the inverse side consistent
	 * with each other when they change.
	 */
	public void unchain() {

		// MonitorWinEventInfoEntity
		if (this.monitorWinEventInfoEntity != null) {
			List<MonitorWinEventIdInfoEntity> list = this.monitorWinEventInfoEntity.getMonitorWinEventIdInfoEntities();
			if (list != null) {
				Iterator<MonitorWinEventIdInfoEntity> iter = list.iterator();
				while(iter.hasNext()) {
					MonitorWinEventIdInfoEntity entity = iter.next();
					if (entity.getId().getMonitorId().equals(this.getId().getMonitorId())){
						iter.remove();
						break;
					}
				}
			}
		}
	}

}