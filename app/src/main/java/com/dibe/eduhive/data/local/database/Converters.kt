package com.dibe.eduhive.data.local.database

import androidx.room.TypeConverter
import com.dibe.eduhive.data.local.entity.MaterialType

class EnumConverters {

    @TypeConverter
    fun toMaterialType(value: String) = enumValueOf<MaterialType>(value)

    @TypeConverter
    fun fromMaterialType(type: MaterialType) = type.name
}
