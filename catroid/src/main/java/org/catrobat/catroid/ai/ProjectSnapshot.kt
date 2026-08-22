/*
 * Catroid: An on-device visual programming system for Android devices
 * Copyright (C) 2010-2026 The Catrobat Team
 * (<http://developer.catrobat.org/credits>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * An additional term exception under section 7 of the GNU Affero
 * General Public License, version 3, is available at
 * http://developer.catrobat.org/license_additional_term
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.catrobat.catroid.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.catrobat.catroid.content.BroadcastScript
import org.catrobat.catroid.content.Project
import org.catrobat.catroid.content.Script
import org.catrobat.catroid.content.StartScript
import org.catrobat.catroid.content.WhenBackgroundChangesScript
import org.catrobat.catroid.content.WhenScript

/**
 * Строит JSON-описание текущего состояния проекта: сцены, спрайты,
 * образы, звуки и скрипты с блоками. Это «вид» проекта, который
 * ИИ-помощник получает перед каждым своим ходом.
 */
object ProjectSnapshot {

    fun build(project: Project): String {
        val root = JsonObject()
        root.addProperty("project_name", project.name)
        val scenes = JsonArray()
        project.sceneList.forEach { scene ->
            val sceneJson = JsonObject()
            sceneJson.addProperty("name", scene.name)
            val sprites = JsonArray()
            scene.spriteList.forEach { sprite ->
                val spriteJson = JsonObject()
                spriteJson.addProperty("name", sprite.name)
                spriteJson.addProperty("is_background", sprite === scene.getBackgroundSprite())
                val looks = JsonArray()
                sprite.lookList.forEach { looks.add(it.name) }
                spriteJson.add("looks", looks)
                val sounds = JsonArray()
                sprite.soundList.forEach { sounds.add(it.name) }
                spriteJson.add("sounds", sounds)
                val scripts = JsonArray()
                sprite.scriptList.forEach { script ->
                    val scriptJson = JsonObject()
                    scriptJson.addProperty("script_type", scriptTypeName(script))
                    val bricks = JsonArray()
                    script.brickList.forEach { bricks.add(it.javaClass.simpleName.removeSuffix("Brick")) }
                    scriptJson.add("bricks", bricks)
                    scripts.add(scriptJson)
                }
                spriteJson.add("scripts", scripts)
                sprites.add(spriteJson)
            }
            sceneJson.add("sprites", sprites)
            scenes.add(sceneJson)
        }
        root.add("scenes", scenes)
        return root.toString()
    }

    private fun scriptTypeName(script: Script): String = when (script) {
        is StartScript -> "start"
        is WhenScript -> "tapped"
        is BroadcastScript -> "receive"
        is WhenBackgroundChangesScript -> "background_changes"
        else -> script.javaClass.simpleName
    }
}
