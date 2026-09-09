package com.polaris.ai.core.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.polaris.ai.core.context.CallerUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * MyBatis-Plus 智能审计元数据自动填充器 (AutoAuditMetaObjectHandler)。
 * <p>
 * 在 insert/update 时自动从当前 CallerContext 提取操作人和租户信息并智能填充。
 * 遵循安全加固原则：仅在实体字段为 null 时自动填充（Only If Absent），保留业务层或超管跨租户显式设置的值。
 *
 * @author polaris
 */
@Component
public class AutoAuditMetaObjectHandler implements MetaObjectHandler {

    private static final String FIELD_CREATE_BY = "createBy";
    private static final String FIELD_CREATE_TIME = "createTime";
    private static final String FIELD_UPDATE_BY = "updateBy";
    private static final String FIELD_UPDATE_TIME = "updateTime";
    private static final String FIELD_TENANT_ID = "tenantId";

    @Override
    public void insertFill(MetaObject metaObject) {
        String username = CallerUtils.getUsername();
        Date now = new Date();

        // 1. 创建者填充（仅当字段为空时）
        if (getFieldValByName(FIELD_CREATE_BY, metaObject) == null) {
            this.strictInsertFill(metaObject, FIELD_CREATE_BY, String.class, username);
        }

        // 2. 创建时间填充
        if (getFieldValByName(FIELD_CREATE_TIME, metaObject) == null) {
            this.strictInsertFill(metaObject, FIELD_CREATE_TIME, Date.class, now);
        }

        // 3. 更新者与更新时间初始填充
        if (getFieldValByName(FIELD_UPDATE_BY, metaObject) == null) {
            this.strictInsertFill(metaObject, FIELD_UPDATE_BY, String.class, username);
        }
        if (getFieldValByName(FIELD_UPDATE_TIME, metaObject) == null) {
            this.strictInsertFill(metaObject, FIELD_UPDATE_TIME, Date.class, now);
        }

        // 4. 租户ID填充（仅中台模式下且实体字段为空时）
        String tenantIdStr = CallerUtils.getTenantId();
        if (tenantIdStr != null && getFieldValByName(FIELD_TENANT_ID, metaObject) == null) {
            try {
                Long tenantIdLong = Long.parseLong(tenantIdStr);
                this.strictInsertFill(metaObject, FIELD_TENANT_ID, Long.class, tenantIdLong);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        String username = CallerUtils.getUsername();
        Date now = new Date();

        // 更新操作人与更新时间
        this.strictUpdateFill(metaObject, FIELD_UPDATE_BY, String.class, username);
        this.strictUpdateFill(metaObject, FIELD_UPDATE_TIME, Date.class, now);
    }
}
