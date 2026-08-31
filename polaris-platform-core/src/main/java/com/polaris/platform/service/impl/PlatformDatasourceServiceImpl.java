package com.polaris.platform.service.impl;

import com.polaris.ai.workflow.spi.WorkflowResourceUsageInspector;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.connector.ConnectorCredentialCipher;
import com.polaris.platform.connector.DatasourceConnectionTestResult;
import com.polaris.platform.connector.DatasourceConnectorExecutor;
import com.polaris.platform.connector.DatasourceJdbcUrlFactory;
import com.polaris.platform.domain.PlatformDatasource;
import com.polaris.platform.domain.PlatformDatasourceVersion;
import com.polaris.platform.dto.DatasourceCredentialRequest;
import com.polaris.platform.dto.DatasourceSaveRequest;
import com.polaris.platform.dto.DatasourceTestResponse;
import com.polaris.platform.mapper.PlatformDatasourceMapper;
import com.polaris.platform.mapper.PlatformDatasourceVersionMapper;
import com.polaris.platform.service.IPlatformDatasourceService;
import com.polaris.platform.tenant.PlatformTenantGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 中台外部数据源服务层实现类。
 *
 * <p>数据源主体用于稳定引用，连接信息通过不可变版本保存。每次修改先验证，
 * 再生成新版本并原子切换当前版本，保证工作流可以固定到已验证配置。</p>
 */
@Service
public class PlatformDatasourceServiceImpl implements IPlatformDatasourceService {

    private static final Set<String> TYPES = Set.of("MYSQL", "POSTGRESQL", "SQLSERVER");
    private static final Set<String> STATUSES = Set.of("0", "1");
    private static final Set<String> CREDENTIAL_ACTIONS = Set.of("KEEP", "REPLACE");

    @Autowired
    private PlatformDatasourceMapper platformDatasourceMapper;

    @Autowired
    private PlatformDatasourceVersionMapper platformDatasourceVersionMapper;

    @Autowired
    private DatasourceConnectorExecutor datasourceConnectorExecutor;

    @Autowired
    private DatasourceJdbcUrlFactory jdbcUrlFactory;

    @Autowired
    private ConnectorCredentialCipher credentialCipher;

    @Autowired(required = false)
    private List<WorkflowResourceUsageInspector> usageInspectors = List.of();

    @Override
    public List<PlatformDatasource> selectDatasourceListByTenantId(Long tenantId) {
        if (!PlatformTenantGuard.belongsToCurrentTenant(tenantId)) {
            return List.of();
        }
        return platformDatasourceMapper.selectByTenantId(tenantId);
    }

    @Override
    public List<PlatformDatasource> selectDatasourceList(PlatformDatasource datasource) {
        PlatformDatasource query = datasource == null ? new PlatformDatasource() : datasource;
        query.setTenantId(PlatformTenantGuard.requireTenantId());
        return platformDatasourceMapper.selectList(query);
    }

    @Override
    public PlatformDatasource selectDatasourceById(Long id) {
        PlatformDatasource datasource = platformDatasourceMapper.selectById(id);
        return datasource != null && PlatformTenantGuard.belongsToCurrentTenant(datasource.getTenantId())
                ? datasource : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformDatasource insertDatasource(DatasourceSaveRequest request, String operator) {
        return insertDatasource(request, operator, PlatformTenantGuard.requireTenantId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformDatasource insertSharedDatasource(DatasourceSaveRequest request, String operator) {
        return insertDatasource(request, operator, null);
    }

    private PlatformDatasource insertDatasource(
            DatasourceSaveRequest request, String operator, Long tenantId) {
        PlatformDatasource datasource = new PlatformDatasource();
        datasource.setTenantId(tenantId);
        applyAssetFields(datasource, request, true);
        applyConnectionFields(datasource, request, null, true);
        DatasourceConnectionTestResult testResult = requireSuccessfulTest(datasource);

        datasource.setCreateBy(operator);
        datasource.setUpdateBy(operator);
        if (platformDatasourceMapper.insert(datasource) != 1) {
            throw new ServiceException("创建数据源失败");
        }
        PlatformDatasourceVersion version = createVersion(datasource, 1, testResult, operator);
        if (platformDatasourceVersionMapper.insert(version) != 1
                || platformDatasourceMapper.updateCurrentVersion(datasource.getId(), version.getId(), operator) != 1) {
            throw new ServiceException("创建数据源连接版本失败");
        }
        return platformDatasourceMapper.selectById(datasource.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlatformDatasource updateDatasource(DatasourceSaveRequest request, String operator) {
        if (request == null || request.getId() == null) {
            throw new ServiceException("数据源 ID 不能为空");
        }
        PlatformDatasource current = selectDatasourceById(request.getId());
        if (current == null) {
            throw new ServiceException("数据源不存在或无权访问");
        }

        PlatformDatasource datasource = new PlatformDatasource();
        datasource.setId(current.getId());
        datasource.setTenantId(current.getTenantId());
        datasource.setCreateBy(current.getCreateBy());
        datasource.setCreateTime(current.getCreateTime());
        datasource.setUpdateBy(operator);
        applyAssetFields(datasource, request, false);
        applyConnectionFields(datasource, request, current, false);
        DatasourceConnectionTestResult testResult = requireSuccessfulTest(datasource);

        if (platformDatasourceMapper.update(datasource) != 1) {
            throw new ServiceException("修改数据源失败");
        }
        int nextVersion = platformDatasourceVersionMapper.selectNextVersionNo(datasource.getId());
        PlatformDatasourceVersion version = createVersion(datasource, nextVersion, testResult, operator);
        if (platformDatasourceVersionMapper.insert(version) != 1
                || platformDatasourceMapper.updateCurrentVersion(datasource.getId(), version.getId(), operator) != 1) {
            throw new ServiceException("切换数据源连接版本失败");
        }
        return platformDatasourceMapper.selectById(datasource.getId());
    }

    @Override
    public int deleteDatasourceById(Long id) {
        PlatformDatasource datasource = selectDatasourceById(id);
        if (datasource == null) {
            return 0;
        }
        long usages = countUsages(datasource);
        if (usages > 0) {
            throw new ServiceException("数据源正被 " + usages + " 个工作流资源绑定使用，请先解除绑定");
        }
        return platformDatasourceMapper.deleteById(id);
    }

    @Override
    public long countDatasourceUsages(Long id) {
        PlatformDatasource datasource = selectDatasourceById(id);
        if (datasource == null) {
            throw new ServiceException("数据源不存在或无权访问");
        }
        return countUsages(datasource);
    }

    @Override
    public DatasourceTestResponse testDatasourceConnection(DatasourceSaveRequest request) {
        PlatformDatasource current = request != null && request.getId() != null
                ? selectDatasourceById(request.getId()) : null;
        if (request != null && request.getId() != null && current == null) {
            throw new ServiceException("数据源不存在或无权访问");
        }
        PlatformDatasource candidate = new PlatformDatasource();
        candidate.setId(current == null ? null : current.getId());
        applyAssetFields(candidate, request, current == null);
        applyConnectionFields(candidate, request, current, current == null);
        return toResponse(datasourceConnectorExecutor.testConnection(candidate));
    }

    @Override
    public DatasourceTestResponse testSavedDatasourceConnection(Long id) {
        PlatformDatasource datasource = selectDatasourceById(id);
        if (datasource == null) {
            throw new ServiceException("数据源不存在或无权访问");
        }
        return toResponse(datasourceConnectorExecutor.testConnection(datasource));
    }

    @Override
    public List<Map<String, Object>> executeDatasourceQuery(
            Long id,
            String sql,
            Map<String, Object> parameters,
            int maxRows,
            Integer queryTimeoutSeconds) {
        PlatformDatasource datasource = selectDatasourceById(id);
        return executeDatasourceQuery(datasource, sql, parameters, maxRows, queryTimeoutSeconds);
    }

    @Override
    public List<Map<String, Object>> executeWorkflowDatasourceQuery(
            Long tenantId,
            Long id,
            String sql,
            Map<String, Object> parameters,
            int maxRows,
            Integer queryTimeoutSeconds) {
        PlatformDatasource datasource = platformDatasourceMapper.selectWorkflowResource(tenantId, id);
        return executeDatasourceQuery(datasource, sql, parameters, maxRows, queryTimeoutSeconds);
    }

    private List<Map<String, Object>> executeDatasourceQuery(
            PlatformDatasource datasource,
            String sql,
            Map<String, Object> parameters,
            int maxRows,
            Integer queryTimeoutSeconds) {
        if (datasource == null) {
            throw new ServiceException("数据源不存在或无权访问");
        }
        if (!"0".equals(datasource.getStatus())
                || !"AVAILABLE".equals(datasource.getVerificationStatus())) {
            throw new ServiceException("数据源未启用或尚未通过连接验证");
        }
        return datasourceConnectorExecutor.executeQuery(
                datasource, sql, parameters, maxRows, queryTimeoutSeconds);
    }

    private void applyAssetFields(
            PlatformDatasource datasource,
            DatasourceSaveRequest request,
            boolean creating) {
        if (request == null) {
            throw new ServiceException("数据源请求不能为空");
        }
        String name = trim(request.getDsName());
        if (name == null) {
            throw new ServiceException("数据源名称不能为空");
        }
        if (name.length() > 128) {
            throw new ServiceException("数据源名称不能超过 128 个字符");
        }
        String type = normalize(request.getDsType());
        if (!TYPES.contains(type)) {
            throw new ServiceException("数据库类型仅支持 MYSQL、POSTGRESQL 或 SQLSERVER");
        }
        String status = trim(request.getStatus());
        if (status == null) status = "0";
        if (!STATUSES.contains(status)) {
            throw new ServiceException("数据源状态仅支持 0（正常）或 1（停用）");
        }
        datasource.setDsName(name);
        datasource.setDsType(type);
        datasource.setStatus(status);
        datasource.setRemark(request.getRemark());
    }

    private void applyConnectionFields(
            PlatformDatasource datasource,
            DatasourceSaveRequest request,
            PlatformDatasource current,
            boolean creating) {
        String host = trim(request.getHost());
        String databaseName = trim(request.getDatabaseName());
        String username = trim(request.getUsername());
        if (host == null || databaseName == null || username == null) {
            throw new ServiceException("数据库主机、数据库名称和用户名不能为空");
        }
        if (username.length() > 128) {
            throw new ServiceException("数据库用户名不能超过 128 个字符");
        }
        datasource.setHost(host);
        datasource.setPort(request.getPort() == null
                ? jdbcUrlFactory.defaultPort(datasource.getDsType()) : request.getPort());
        datasource.setDatabaseName(databaseName);
        datasource.setUsername(username);
        datasource.setSslEnabled(Boolean.TRUE.equals(request.getSslEnabled()));
        datasource.setConnectTimeoutSeconds(request.getConnectTimeoutSeconds() == null
                ? 5 : request.getConnectTimeoutSeconds());
        datasource.setQueryTimeoutSeconds(request.getQueryTimeoutSeconds() == null
                ? 10 : request.getQueryTimeoutSeconds());
        // 复用 URL 工厂完成主机、端口、数据库名和超时范围校验。
        jdbcUrlFactory.build(datasource);
        applyCredential(datasource, request, current, creating);
    }

    private void applyCredential(
            PlatformDatasource datasource,
            DatasourceSaveRequest request,
            PlatformDatasource current,
            boolean creating) {
        String action = normalize(request.getCredentialAction());
        if (action == null) action = creating ? "REPLACE" : "KEEP";
        if (!CREDENTIAL_ACTIONS.contains(action)) {
            throw new ServiceException("密码处理方式仅支持 KEEP 或 REPLACE");
        }
        if ("KEEP".equals(action)) {
            if (current == null || trim(current.getPassword()) == null) {
                throw new ServiceException("当前数据源尚未配置密码");
            }
            String password = current.getPassword();
            // 兼容开发阶段遗留明文；再次保存时自动升级为密文信封。
            datasource.setPassword(credentialCipher.isEncrypted(password)
                    ? password : credentialCipher.encrypt(password));
            return;
        }
        DatasourceCredentialRequest credential = request.getCredential();
        String password = credential == null ? null : credential.getPassword();
        if (password == null || password.isEmpty()) {
            throw new ServiceException("数据库密码不能为空");
        }
        if (password.length() > 1024) {
            throw new ServiceException("数据库密码不能超过 1024 个字符");
        }
        datasource.setPassword(credentialCipher.encrypt(password));
    }

    private DatasourceConnectionTestResult requireSuccessfulTest(PlatformDatasource datasource) {
        DatasourceConnectionTestResult result = datasourceConnectorExecutor.testConnection(datasource);
        if (!result.success()) {
            throw new ServiceException(result.errorMessage());
        }
        return result;
    }

    private PlatformDatasourceVersion createVersion(
            PlatformDatasource datasource,
            int versionNo,
            DatasourceConnectionTestResult testResult,
            String operator) {
        PlatformDatasourceVersion version = new PlatformDatasourceVersion();
        version.setTenantId(datasource.getTenantId());
        version.setDatasourceId(datasource.getId());
        version.setVersionNo(versionNo);
        version.setHost(datasource.getHost());
        version.setPort(datasource.getPort());
        version.setDatabaseName(datasource.getDatabaseName());
        version.setUsername(datasource.getUsername());
        version.setPassword(datasource.getPassword());
        version.setSslEnabled(datasource.getSslEnabled());
        version.setConnectTimeoutSeconds(datasource.getConnectTimeoutSeconds());
        version.setQueryTimeoutSeconds(datasource.getQueryTimeoutSeconds());
        version.setVerificationStatus("AVAILABLE");
        version.setDatabaseProductName(testResult.databaseProductName());
        version.setDatabaseProductVersion(testResult.databaseProductVersion());
        version.setLastTestTime(new Date());
        version.setLastTestLatencyMs(testResult.responseTimeMs());
        version.setLastTestErrorCode(null);
        version.setCreateBy(operator);
        return version;
    }

    private DatasourceTestResponse toResponse(DatasourceConnectionTestResult result) {
        return new DatasourceTestResponse(
                result.success(), result.responseTimeMs(), result.databaseProductName(),
                result.databaseProductVersion(), result.errorCode(), result.errorMessage());
    }

    private long countUsages(PlatformDatasource datasource) {
        return usageInspectors.stream()
                .mapToLong(inspector -> inspector.countActiveUsages(
                        datasource.getTenantId(), "DATASOURCE", String.valueOf(datasource.getId())))
                .sum();
    }

    private String normalize(String value) {
        String result = trim(value);
        return result == null ? null : result.toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
