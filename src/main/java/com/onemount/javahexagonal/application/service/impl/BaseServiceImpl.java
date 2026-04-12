package com.onemount.javahexagonal.application.service.impl;

import com.onemount.javahexagonal.application.converter.AbstractConverter;
import com.onemount.javahexagonal.application.service.BaseService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.PagingAndSortingRepository;

public abstract class BaseServiceImpl<T, D, ID> implements BaseService<D, ID> {

    protected abstract PagingAndSortingRepository<T, ID> getRepository();
    protected abstract AbstractConverter<T, D> getConverter();

    public Page<D> getAll(Integer page, Integer size, String sort, String direction) {
        Sort.Direction sortDirection = "DESC".equalsIgnoreCase(direction) ?
                Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
        Page<T> entityPage = getRepository().findAll(pageable);

        return getConverter().convertToPage(entityPage);
    }
}