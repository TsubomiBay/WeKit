@file:OptIn(ExperimentalSerializationApi::class)

package dev.ujhhgtg.wekit.hooks.api.net.models.protobuf


import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BeforeTransferProto(
    @ProtoNumber(4) val maskedRealName: String? = null,
    @ProtoNumber(5) val key: String? = null
)
