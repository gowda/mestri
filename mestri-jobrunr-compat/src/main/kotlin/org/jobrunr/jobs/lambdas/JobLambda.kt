package org.jobrunr.jobs.lambdas

import `in`.supertiny.mestri.core.model.InvocationDescriptor

fun interface JobLambda {
  fun toInvocationDescriptor(): InvocationDescriptor
}
