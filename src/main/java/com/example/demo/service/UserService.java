package com.example.demo.service;

import com.example.demo.batch.Cargo;
import com.example.demo.dao.UserDao;
import com.example.demo.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserDao userDao;

    public UserService(UserDao userDao) {
        this.userDao = userDao;
    }

    public Page<User> findPage(Pageable pageable) {
        return this.userDao.findAll(pageable);
    }

    @Transactional
    public void saveAll(List<Cargo<User, User>> cargos) {
        List<User> users = cargos.stream().map(Cargo::getContent).collect(Collectors.toList());

        this.userDao.saveAll(users);

        for (Cargo<User, User> cargo : cargos) {
            User user = cargo.getContent();
            cargo.getHearthstone().complete(user);
        }
    }
}
