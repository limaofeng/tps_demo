package com.example.demo.web;

import com.example.demo.batch.BatchService;
import com.example.demo.domain.User;
import com.example.demo.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Controller
public class UserController {

    private final UserService userService;
    private final BatchService<User, User> batchSaveService;

    public UserController(UserService userService) {
        this.userService = userService;
        this.batchSaveService = BatchService.create(UserController.this.userService::saveAll, 500, 8);
    }

    @GetMapping("/users")
    @ResponseBody
    public Page<User> users() {
        return this.userService.findPage(Pageable.ofSize(20));
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    public CompletableFuture<User> addUser(@RequestBody User user) {
        return this.batchSaveService.submit(user);
    }
}
