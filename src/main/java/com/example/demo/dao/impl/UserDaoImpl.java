package com.example.demo.dao.impl;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import com.example.demo.dao.UserDao;
import com.example.demo.domain.User;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.util.Assert;

import javax.persistence.EntityManager;
import java.util.List;

public class UserDaoImpl extends SimpleJpaRepository<User, Long> implements UserDao {

    private final EntityManager em;
    private final Snowflake snowflake = IdUtil.getSnowflake(1, 1);

    public UserDaoImpl(EntityManager entityManager) {
        super(JpaEntityInformationSupport.getEntityInformation(User.class, entityManager), entityManager);
        this.em = entityManager;
    }

    @Override
    public <S extends User> S save(S entity) {
        Assert.notNull(entity, "Entity must not be null.");
        if (entity.getId() == null) {
            entity.setId(snowflake.nextId());
        }
        return super.save(entity);
    }

    public void saveInBatch(List<User> entities) {
        if (entities == null) {
            throw new IllegalArgumentException("The given Iterable of entities cannot be null!");
        }
        entities.forEach(item -> item.setId(snowflake.nextId()));
        for (User entity : entities) {
            this.em.persist(entity);
        }
    }

}
