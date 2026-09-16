package com.polaris.ai.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 工作流定时触发的可靠派发批次。
 *
 * <p>Quartz 只负责在计划时间唤醒任务，真正的工作流执行由异步派发器根据本记录创建。
 * 同一触发器的同一计划时间只允许一条批次记录，用于抵御 Quartz 重复回调、
 * 进程崩溃和派发重试导致的重复执行。</p>
 *
 * <p>状态流转为：{@code PENDING -> DISPATCHING -> DISPATCHED}；派发失败时进入
 * {@code FAILED} 并按时间重试，超过最大次数后进入 {@code DEAD}，触发器已失效时
 * 进入 {@code CANCELLED}。</p>
 */
@Data
@TableName("ai_workflow_trigger_fire")
public class WorkflowTriggerFire implements Serializable {

    /** Java 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户 ID；系统级工作流为 {@code null}。 */
    private Long tenantId;

    /** 对外可见的触发批次 ID，全局唯一。 */
    private String fireId;

    /** 产生该批次的工作流触发器 ID。 */
    private String triggerId;

    /**
     * 本批次在 Cron 时间线上的应触发时间。
     * 与 {@link #triggerId} 共同构成幂等时间槽，不等同于实际派发时间。
     */
    private Date scheduledTime;

    /**
     * 派发状态：{@code PENDING}待派发、{@code DISPATCHING}派发中、
     * {@code DISPATCHED}已派发、{@code FAILED}待重试、{@code CANCELLED}已取消、
     * {@code DEAD}已终止重试。
     */
    private String fireStatus;

    /** 已尝试领取并派发的次数，用于重试上限判定。 */
    private Integer attemptCount;

    /** 当前持有派发租约的派发器实例标识；未被领取时为 {@code null}。 */
    private String claimedBy;

    /** 当前派发租约的到期时间；超时后其他派发器可恢复领取。 */
    private Date claimUntil;

    /** {@code FAILED} 状态下允许再次派发的最早时间。 */
    private Date nextRetryTime;

    /** 派发成功后创建的工作流执行 ID。 */
    private String executionId;

    /** 最近一次派发失败或取消原因的脱敏摘要。 */
    private String errorMessage;

    /** 工作流执行创建成功、批次进入 {@code DISPATCHED} 的时间。 */
    private Date dispatchedTime;

    /** 批次记录创建时间。 */
    private Date createTime;

    /** 批次记录最后更新时间。 */
    private Date updateTime;
}
