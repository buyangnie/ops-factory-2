/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.gateway.common.model;

import com.huawei.opsfactory.gateway.common.constants.GatewayConstants;

import java.util.Set;

/**
 * User role enumeration.
 *
 * @author x00000000
 * @since 2026-05-09
 */
public enum UserRole {
    ADMIN,
    USER;

    /**
     * Resolves the user role from the given user identifier.
     *
     * @param userId the userId parameter
     * @return the result
     */
    public static UserRole fromUserId(String userId) {
        return GatewayConstants.SYSTEM_USER.equals(userId) ? ADMIN : USER;
    }

    /**
     * Resolves the user role from the given user identifier and admin user set.
     *
     * @param userId the userId parameter
     * @param adminUsers the adminUsers parameter
     * @return the result
     */
    public static UserRole fromUserId(String userId, Set<String> adminUsers) {
        return adminUsers.contains(userId) ? ADMIN : USER;
    }

    /**
     * Checks whether this role represents an administrator.
     *
     * @return the result
     */
    public boolean isAdmin() {
        return this == ADMIN;
    }
}
