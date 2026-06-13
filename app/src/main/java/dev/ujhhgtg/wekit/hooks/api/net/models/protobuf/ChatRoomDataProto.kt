@file:OptIn(ExperimentalSerializationApi::class)

package dev.ujhhgtg.wekit.hooks.api.net.models.protobuf

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber


@Serializable
data class ChatRoomDataProto(
    @ProtoNumber(1) val members: List<ChatRoomMemberProto> = emptyList(),
)

@Serializable
data class ChatRoomMemberProto(
    @ProtoNumber(1) val wxId: String = "",
    @ProtoNumber(2) val displayName: String = "",
)
