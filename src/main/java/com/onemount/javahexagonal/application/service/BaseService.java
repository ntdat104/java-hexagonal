package com.onemount.javahexagonal.application.service;

import org.springframework.data.domain.Page;

public interface BaseService<D, I> { // D: DTO, I: ID type
    Page<D> getAll(Integer page, Integer size, String sort, String direction);
}
