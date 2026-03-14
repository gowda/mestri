package `in`.supertiny.mestri.core.runtime

import `in`.supertiny.mestri.core.model.JobId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RuntimeContractsTest {

  @Test
  fun `job lease detects expiration`() {
    val acquiredAt = Instant.parse("2026-03-14T10:00:00Z")
    val lease =
      JobLease(
        jobId = JobId("job-1"),
        workerId = WorkerId("worker-1"),
        acquiredAt = acquiredAt,
        expiresAt = Instant.parse("2026-03-14T10:05:00Z"),
      )

    assertTrue(lease.isExpired(Instant.parse("2026-03-14T10:05:00Z")))
  }

  @Test
  fun `worker heartbeat ttl drops to zero once stale`() {
    val heartbeat =
      WorkerHeartbeat(
        workerId = WorkerId("worker-1"),
        processId = ProcessId("process-1"),
        queues = setOf("default", "critical"),
        recordedAt = Instant.parse("2026-03-14T10:00:00Z"),
        expiresAt = Instant.parse("2026-03-14T10:00:30Z"),
      )

    assertEquals(java.time.Duration.ZERO, heartbeat.ttl(Instant.parse("2026-03-14T10:01:00Z")))
  }

  @Test
  fun `worker heartbeat reports positive ttl before expiry`() {
    val heartbeat =
      WorkerHeartbeat(
        workerId = WorkerId("worker-1"),
        processId = ProcessId("process-1"),
        queues = setOf("default"),
        recordedAt = Instant.parse("2026-03-14T10:00:00Z"),
        expiresAt = Instant.parse("2026-03-14T10:00:30Z"),
      )

    assertTrue(heartbeat.ttl(Instant.parse("2026-03-14T10:00:10Z")).seconds > 0)
  }

  @Test
  fun `maintenance cleanup bucket rejects blank bucket name`() {
    assertFailsWith<IllegalArgumentException> {
      CleanupRetainedBucket(bucketName = "")
    }
  }

  @Test
  fun `worker heartbeat rejects blank queue names`() {
    assertFailsWith<IllegalArgumentException> {
      WorkerHeartbeat(
        workerId = WorkerId("worker-1"),
        processId = ProcessId("process-1"),
        queues = setOf(""),
        recordedAt = Instant.parse("2026-03-14T10:00:00Z"),
        expiresAt = Instant.parse("2026-03-14T10:00:30Z"),
      )
    }
  }

  @Test
  fun `job lease rejects non increasing expiry`() {
    assertFailsWith<IllegalArgumentException> {
      JobLease(
        jobId = JobId("job-1"),
        workerId = WorkerId("worker-1"),
        acquiredAt = Instant.parse("2026-03-14T10:00:00Z"),
        expiresAt = Instant.parse("2026-03-14T10:00:00Z"),
      )
    }
  }
}
