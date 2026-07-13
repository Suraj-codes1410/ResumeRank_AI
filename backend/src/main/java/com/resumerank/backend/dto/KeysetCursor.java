package com.resumerank.backend.dto;

import java.util.UUID;

public class KeysetCursor {
    private UUID id;
    private String sortValue;
    private String sortType;

    public KeysetCursor() {
    }

    public KeysetCursor(UUID id, String sortValue, String sortType) {
        this.id = id;
        this.sortValue = sortValue;
        this.sortType = sortType;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSortValue() {
        return sortValue;
    }

    public void setSortValue(String sortValue) {
        this.sortValue = sortValue;
    }

    public String getSortType() {
        return sortType;
    }

    public void setSortType(String sortType) {
        this.sortType = sortType;
    }
}
