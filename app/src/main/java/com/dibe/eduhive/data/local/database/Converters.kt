package com.dibe.eduhive.data.local.database

import androidx.room.TypeConverter
import com.dibe.eduhive.domain.model.MaterialType

class EnumConverters {

    @TypeConverter
    fun toMaterialType(value: String) = enumValueOf<MaterialType>(value)

    @TypeConverter
    fun fromMaterialType(type: MaterialType) = type.name
}
