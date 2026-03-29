package com.vaultguard.db.repository;

import com.vaultguard.db.entity.CollectionUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CollectionUserRepository extends JpaRepository<CollectionUser, CollectionUser.CollectionUserId> {
    List<CollectionUser> findByOrgMembershipUuid(String orgMembershipUuid);
    List<CollectionUser> findByCollectionUuid(String collectionUuid);
}
