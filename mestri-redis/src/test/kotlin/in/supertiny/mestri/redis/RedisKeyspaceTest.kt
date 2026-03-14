package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.runtime.ProcessId
import `in`.supertiny.mestri.core.runtime.WorkerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RedisKeyspaceTest {

  @Test
  fun `queue key uses queue namespace`() {
    assertEquals("mestri:queue:critical", RedisKeyspace.queue(QueueName("critical")))
  }

  @Test
  fun `bucket keys use stable names`() {
    assertEquals("mestri:scheduled", RedisKeyspace.scheduled())
    assertEquals("mestri:retry", RedisKeyspace.retry())
    assertEquals("mestri:dead", RedisKeyspace.dead())
    assertEquals("mestri:succeeded", RedisKeyspace.succeeded())
    assertEquals("mestri:deleted", RedisKeyspace.deleted())
  }

  @Test
  fun `job and lease keys use job ids`() {
    val jobId = JobId("job-123")

    assertEquals("mestri:job:job-123", RedisKeyspace.job(jobId))
    assertEquals("mestri:lease:job-123", RedisKeyspace.workerLease(jobId))
  }

  @Test
  fun `process and worker keys are distinct`() {
    assertEquals("mestri:processes", RedisKeyspace.processRegistry())
    assertEquals("mestri:process:process-1", RedisKeyspace.process(ProcessId("process-1")))
    assertEquals(
      "mestri:process:process-1:work",
      RedisKeyspace.processWork(ProcessId("process-1")),
    )
    assertEquals(
      "mestri:worker:worker-1:heartbeat",
      RedisKeyspace.workerHeartbeat(WorkerId("worker-1")),
    )
  }

  @Test
  fun `recurring keys use dedicated namespace`() {
    assertEquals("mestri:recurring", RedisKeyspace.recurringDefinitions())
    assertEquals("mestri:recurring:nightly-sync", RedisKeyspace.recurringDefinition("nightly-sync"))
  }

  @Test
  fun `recurring definition rejects blank id`() {
    assertFailsWith<IllegalArgumentException> {
      RedisKeyspace.recurringDefinition("")
    }
  }
}
