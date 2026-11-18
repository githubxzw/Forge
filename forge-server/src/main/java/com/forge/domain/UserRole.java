package com.forge.domain;

/**
 * 用户角色（三级，小团队够用）：
 * - VIEWER：只读（看构建/部署/制品/日志）
 * - OPERATOR：操作员（发起构建/部署/回滚/审批之外的全部日常操作）
 * - ADMIN：管理员（用户/令牌/设置/凭据/项目/环境管理 + 审批）
 */
public enum UserRole { ADMIN, OPERATOR, VIEWER }
