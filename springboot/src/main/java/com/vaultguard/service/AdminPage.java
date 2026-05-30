package com.vaultguard.service;

import java.util.List;

/** Result of an admin list query: a page slice plus the total count of unfiltered/filtered rows. */
public record AdminPage<T>(List<T> data, long total) {}
