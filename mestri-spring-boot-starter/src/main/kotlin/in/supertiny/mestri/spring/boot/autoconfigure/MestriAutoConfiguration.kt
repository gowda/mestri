package `in`.supertiny.mestri.spring.boot.autoconfigure

import com.fasterxml.jackson.databind.ObjectMapper
import `in`.supertiny.mestri.core.runtime.DefaultMaintenancePlanner
import `in`.supertiny.mestri.core.runtime.JobHandlerInvoker
import `in`.supertiny.mestri.core.runtime.MaintenanceLoop
import `in`.supertiny.mestri.core.runtime.MaintenancePlanner
import `in`.supertiny.mestri.core.runtime.RetryBackoffCalculator
import `in`.supertiny.mestri.core.scheduling.RecurringJobDefinitionStore
import `in`.supertiny.mestri.core.scheduling.RecurringJobScheduler
import `in`.supertiny.mestri.core.serialization.JobPayloadSerializer
import `in`.supertiny.mestri.redis.JacksonJobPayloadSerializer
import `in`.supertiny.mestri.redis.JacksonRecurringJobDefinitionSerializer
import `in`.supertiny.mestri.redis.RedisDeadLetterExecutionHandler
import `in`.supertiny.mestri.redis.RedisDeduplicationStore
import `in`.supertiny.mestri.redis.RedisHashAccessor
import `in`.supertiny.mestri.redis.RedisImmediateJobEnqueuer
import `in`.supertiny.mestri.redis.RedisJobLeaseStore
import `in`.supertiny.mestri.redis.RedisJobRecordMapper
import `in`.supertiny.mestri.redis.RedisJobRecordStore
import `in`.supertiny.mestri.redis.RedisMaintenanceExecutor
import `in`.supertiny.mestri.redis.RedisMaintenanceSnapshotSource
import `in`.supertiny.mestri.redis.RedisQueueAccessor
import `in`.supertiny.mestri.redis.RedisQueueReservationStore
import `in`.supertiny.mestri.redis.RedisRecurringJobDefinitionStore
import `in`.supertiny.mestri.redis.RedisScheduledJobPromoter
import `in`.supertiny.mestri.redis.RedisScheduledJobStore
import `in`.supertiny.mestri.redis.RedisSetAccessor
import `in`.supertiny.mestri.redis.RedisSortedSetAccessor
import `in`.supertiny.mestri.redis.RedisSuccessfulExecutionHandler
import `in`.supertiny.mestri.redis.RedisTerminalJobStore
import `in`.supertiny.mestri.redis.RedisWorkerHeartbeatStore
import `in`.supertiny.mestri.redis.SpringRedisHashAccessor
import `in`.supertiny.mestri.redis.SpringRedisQueueAccessor
import `in`.supertiny.mestri.redis.SpringRedisSetAccessor
import `in`.supertiny.mestri.redis.SpringRedisSortedSetAccessor
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Clock
import java.time.Instant

@AutoConfiguration(
  after = [RedisAutoConfiguration::class, RedisRepositoriesAutoConfiguration::class],
)
@ConditionalOnClass(StringRedisTemplate::class)
@ConditionalOnProperty(
  prefix = "mestri",
  name = ["enabled"],
  havingValue = "true",
  matchIfMissing = true,
)
@EnableConfigurationProperties(MestriProperties::class)
class MestriAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  fun mestriClock(): Clock = Clock.systemUTC()

  @Bean
  @ConditionalOnMissingBean
  fun mestriJobPayloadSerializer(objectMapper: ObjectMapper): JobPayloadSerializer =
    JacksonJobPayloadSerializer(objectMapper.copy())

  @Bean
  @ConditionalOnMissingBean
  fun mestriJobHandlerInvoker(
    applicationContext: ApplicationContext,
    objectMapper: ObjectMapper,
  ): JobHandlerInvoker =
    SpringBeanJobHandlerInvoker(
      applicationContext = applicationContext,
      objectMapper = objectMapper.copy(),
    )

  @Bean
  @ConditionalOnMissingBean
  fun mestriRecurringJobDefinitionSerializer(
    objectMapper: ObjectMapper,
  ): JacksonRecurringJobDefinitionSerializer =
    JacksonRecurringJobDefinitionSerializer(objectMapper.copy())

  @Bean
  @ConditionalOnBean(StringRedisTemplate::class)
  @ConditionalOnMissingBean
  fun mestriRedisHashAccessor(stringRedisTemplate: StringRedisTemplate): RedisHashAccessor =
    SpringRedisHashAccessor(stringRedisTemplate)

  @Bean
  @ConditionalOnBean(StringRedisTemplate::class)
  @ConditionalOnMissingBean
  fun mestriRedisQueueAccessor(stringRedisTemplate: StringRedisTemplate): RedisQueueAccessor =
    SpringRedisQueueAccessor(stringRedisTemplate)

  @Bean
  @ConditionalOnBean(StringRedisTemplate::class)
  @ConditionalOnMissingBean
  fun mestriRedisSetAccessor(stringRedisTemplate: StringRedisTemplate): RedisSetAccessor =
    SpringRedisSetAccessor(stringRedisTemplate)

  @Bean
  @ConditionalOnBean(StringRedisTemplate::class)
  @ConditionalOnMissingBean
  fun mestriRedisSortedSetAccessor(
    stringRedisTemplate: StringRedisTemplate,
  ): RedisSortedSetAccessor = SpringRedisSortedSetAccessor(stringRedisTemplate)

  @Bean
  @ConditionalOnMissingBean
  fun mestriRedisJobRecordMapper(jobPayloadSerializer: JobPayloadSerializer): RedisJobRecordMapper =
    RedisJobRecordMapper(jobPayloadSerializer)

  @Bean
  @ConditionalOnMissingBean
  fun mestriRedisJobRecordStore(
    redisHashAccessor: RedisHashAccessor,
    redisJobRecordMapper: RedisJobRecordMapper,
  ): RedisJobRecordStore = RedisJobRecordStore(redisHashAccessor, redisJobRecordMapper)

  @Bean
  @ConditionalOnMissingBean
  fun mestriRedisDeduplicationStore(redisHashAccessor: RedisHashAccessor): RedisDeduplicationStore =
    RedisDeduplicationStore(redisHashAccessor)

  @Bean
  @ConditionalOnMissingBean
  fun mestriRedisJobLeaseStore(
    redisHashAccessor: RedisHashAccessor,
    redisSetAccessor: RedisSetAccessor,
  ): RedisJobLeaseStore = RedisJobLeaseStore(redisHashAccessor, redisSetAccessor)

  @Bean
  @ConditionalOnMissingBean
  fun mestriRedisWorkerHeartbeatStore(
    redisHashAccessor: RedisHashAccessor,
    redisSetAccessor: RedisSetAccessor,
  ): RedisWorkerHeartbeatStore = RedisWorkerHeartbeatStore(redisHashAccessor, redisSetAccessor)

  @Bean
  @ConditionalOnMissingBean
  fun mestriRedisImmediateJobEnqueuer(
    redisJobRecordStore: RedisJobRecordStore,
    redisQueueAccessor: RedisQueueAccessor,
    redisDeduplicationStore: RedisDeduplicationStore,
    clock: Clock,
  ): RedisImmediateJobEnqueuer =
    RedisImmediateJobEnqueuer(
      jobRecordStore = redisJobRecordStore,
      listAccessor = redisQueueAccessor,
      deduplicationStore = redisDeduplicationStore,
      clock = { Instant.now(clock) },
    )

  @Bean
  @ConditionalOnMissingBean
  fun mestriRedisScheduledJobStore(
    redisJobRecordStore: RedisJobRecordStore,
    redisSortedSetAccessor: RedisSortedSetAccessor,
    redisDeduplicationStore: RedisDeduplicationStore,
    clock: Clock,
  ): RedisScheduledJobStore =
    RedisScheduledJobStore(
      jobRecordStore = redisJobRecordStore,
      sortedSetAccessor = redisSortedSetAccessor,
      deduplicationStore = redisDeduplicationStore,
      clock = { Instant.now(clock) },
    )

  @Bean
  @ConditionalOnMissingBean
  fun mestriRedisTerminalJobStore(
    redisJobRecordStore: RedisJobRecordStore,
    redisSortedSetAccessor: RedisSortedSetAccessor,
  ): RedisTerminalJobStore =
    RedisTerminalJobStore(
      jobRecordStore = redisJobRecordStore,
      sortedSetAccessor = redisSortedSetAccessor,
    )

  @Bean
  @ConditionalOnMissingBean
  fun mestriRedisQueueReservationStore(
    redisQueueAccessor: RedisQueueAccessor,
    redisJobRecordStore: RedisJobRecordStore,
    redisJobLeaseStore: RedisJobLeaseStore,
    clock: Clock,
  ): RedisQueueReservationStore =
    RedisQueueReservationStore(
      queueAccessor = redisQueueAccessor,
      jobRecordStore = redisJobRecordStore,
      leaseStore = redisJobLeaseStore,
      clock = { Instant.now(clock) },
    )

  @Bean
  @ConditionalOnMissingBean
  fun mestriRetryBackoffCalculator(): RetryBackoffCalculator = RetryBackoffCalculator()

  @Bean
  @ConditionalOnMissingBean
  fun mestriRedisSuccessfulExecutionHandler(
    redisTerminalJobStore: RedisTerminalJobStore,
    redisJobLeaseStore: RedisJobLeaseStore,
  ): RedisSuccessfulExecutionHandler =
    RedisSuccessfulExecutionHandler(redisTerminalJobStore, redisJobLeaseStore)

  @Bean
  @ConditionalOnMissingBean
  fun mestriRedisRetryExecutionHandler(
    redisScheduledJobStore: RedisScheduledJobStore,
    redisJobLeaseStore: RedisJobLeaseStore,
    retryBackoffCalculator: RetryBackoffCalculator,
    clock: Clock,
  ): `in`.supertiny.mestri.redis.RedisRetryExecutionHandler =
    `in`.supertiny.mestri.redis.RedisRetryExecutionHandler(
      scheduledJobStore = redisScheduledJobStore,
      leaseStore = redisJobLeaseStore,
      backoffCalculator = retryBackoffCalculator,
      clock = { Instant.now(clock) },
    )

  @Bean
  @ConditionalOnMissingBean
  fun mestriRedisDeadLetterExecutionHandler(
    redisScheduledJobStore: RedisScheduledJobStore,
    redisTerminalJobStore: RedisTerminalJobStore,
    redisJobLeaseStore: RedisJobLeaseStore,
    clock: Clock,
  ): RedisDeadLetterExecutionHandler =
    RedisDeadLetterExecutionHandler(
      scheduledJobStore = redisScheduledJobStore,
      terminalJobStore = redisTerminalJobStore,
      leaseStore = redisJobLeaseStore,
      clock = { Instant.now(clock) },
    )

  @Bean
  @ConditionalOnMissingBean
  fun mestriRedisMaintenanceSnapshotSource(
    redisJobLeaseStore: RedisJobLeaseStore,
    redisWorkerHeartbeatStore: RedisWorkerHeartbeatStore,
  ): RedisMaintenanceSnapshotSource =
    RedisMaintenanceSnapshotSource(redisJobLeaseStore, redisWorkerHeartbeatStore)

  @Bean
  @ConditionalOnMissingBean
  fun mestriMaintenancePlanner(): MaintenancePlanner = DefaultMaintenancePlanner()

  @Bean
  @ConditionalOnMissingBean
  fun mestriRedisMaintenanceExecutor(
    redisJobRecordStore: RedisJobRecordStore,
    redisQueueAccessor: RedisQueueAccessor,
    redisScheduledJobStore: RedisScheduledJobStore,
    redisSortedSetAccessor: RedisSortedSetAccessor,
    redisJobLeaseStore: RedisJobLeaseStore,
    redisWorkerHeartbeatStore: RedisWorkerHeartbeatStore,
    retryBackoffCalculator: RetryBackoffCalculator,
  ): RedisMaintenanceExecutor =
    RedisMaintenanceExecutor(
      jobRecordStore = redisJobRecordStore,
      listAccessor = redisQueueAccessor,
      scheduledJobStore = redisScheduledJobStore,
      sortedSetAccessor = redisSortedSetAccessor,
      leaseStore = redisJobLeaseStore,
      heartbeatStore = redisWorkerHeartbeatStore,
      backoffCalculator = retryBackoffCalculator,
    )

  @Bean
  @ConditionalOnMissingBean
  fun mestriMaintenanceLoop(
    maintenanceSnapshotSource: RedisMaintenanceSnapshotSource,
    maintenancePlanner: MaintenancePlanner,
    redisMaintenanceExecutor: RedisMaintenanceExecutor,
    clock: Clock,
  ): MaintenanceLoop =
    MaintenanceLoop(
      snapshotSource = maintenanceSnapshotSource,
      planner = maintenancePlanner,
      executor = redisMaintenanceExecutor,
      clock = { Instant.now(clock) },
    )

  @Bean
  @ConditionalOnMissingBean
  fun mestriRedisScheduledJobPromoter(
    redisJobRecordStore: RedisJobRecordStore,
    redisSortedSetAccessor: RedisSortedSetAccessor,
    redisQueueAccessor: RedisQueueAccessor,
    clock: Clock,
  ): RedisScheduledJobPromoter =
    RedisScheduledJobPromoter(
      jobRecordStore = redisJobRecordStore,
      sortedSetAccessor = redisSortedSetAccessor,
      listAccessor = redisQueueAccessor,
      clock = { Instant.now(clock) },
    )

  @Bean
  @ConditionalOnMissingBean
  fun mestriRecurringJobDefinitionStore(
    redisHashAccessor: RedisHashAccessor,
    redisSetAccessor: RedisSetAccessor,
    recurringJobDefinitionSerializer: JacksonRecurringJobDefinitionSerializer,
  ): RecurringJobDefinitionStore =
    RedisRecurringJobDefinitionStore(
      hashAccessor = redisHashAccessor,
      setAccessor = redisSetAccessor,
      serializer = recurringJobDefinitionSerializer,
    )

  @Bean
  @ConditionalOnMissingBean
  fun mestriRecurringJobScheduler(
    recurringJobDefinitionStore: RecurringJobDefinitionStore,
    clock: Clock,
  ): RecurringJobScheduler =
    RecurringJobScheduler(
      store = recurringJobDefinitionStore,
      clock = { Instant.now(clock) },
    )
}
