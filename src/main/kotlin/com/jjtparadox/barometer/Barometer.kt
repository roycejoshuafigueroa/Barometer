/*
 * This file is part of Barometer
 *
 * Copyright (c) 2017 jjtParadox
 *
 * Barometer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Barometer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Barometer. If not, see <http://www.gnu.org/licenses/>.
 */
package com.jjtparadox.barometer

import com.google.common.collect.Queues
import net.minecraft.launchwrapper.Launch
import net.minecraft.server.dedicated.DedicatedServer
import net.minecraft.server.dedicated.PropertyManager
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraftforge.common.ForgeChunkManager
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent
import net.minecraftforge.fml.common.event.FMLServerStartedEvent
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.util.Queue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask

val LOGGER: Logger = LogManager.getLogger(Barometer.MOD_ID)

@Mod(modid = Barometer.MOD_ID, version = Barometer.VERSION, serverSideOnly = true)
class Barometer {
    companion object {
        const val MOD_ID = "barometer"
        const val VERSION = "0.0.2"

        @JvmField val futureTaskQueue: Queue<FutureTask<*>> = Queues.newArrayDeque<FutureTask<*>>()
        @JvmField var testing = true
        @JvmField var finishedLatch = CountDownLatch(1)

        val server by lazy { theServer } // Hack to create a lateinit val
        private lateinit var theServer: DedicatedServer
    }

    @Mod.EventHandler
    fun preInit(event: FMLPreInitializationEvent) {
        if (Launch.classLoader.getClassBytes("net.minecraft.world.World") == null) {
//            LOGGER.error("Barometer should only be used in a deobsfucated dev environment!")
//            Loader.instance().activeModContainer().setEnabledState(false)
            throw IllegalStateException("The Barometer mod should only be used in a deobsfucated test environment!")
        }

        //TODO Allow all this stuff to be configured from build.gradle (or some better place?)
        theServer = FMLCommonHandler.instance().minecraftServerInstance as DedicatedServer
        val serverSettings = PropertyManager(File("server.properties"))

        serverSettings.setProperty("online-mode", false)
        serverSettings.setProperty("server-ip", "127.0.0.1")
        serverSettings.setProperty("spawn-animals", false)
        serverSettings.setProperty("spawn-npcs", false)
        serverSettings.setProperty("motd", "Barometer Test Server")
        serverSettings.setProperty("force-gamemode", true)
        serverSettings.setProperty("difficulty", 0)
        serverSettings.setProperty("generate-structures", false)
        serverSettings.setProperty("gamemode", 0)
        serverSettings.setProperty("level-type", "FLAT")
        serverSettings.setProperty("max-tick-time", 0)
        serverSettings.saveProperties()

        server.serverOwner = "barometer_test_player"

        MinecraftForge.EVENT_BUS.register(this)
    }

//TODO Make a nice empty world for tests
//    @Mod.EventHandler
//    fun init(event: FMLInitializationEvent) {
//        TestWorldType()
//    }

    @Mod.EventHandler
    fun serverAboutToStart(event: FMLServerAboutToStartEvent) {
    }

    @Mod.EventHandler
    fun serverStarted(event: FMLServerStartedEvent) {
        while (testing) {
            synchronized(futureTaskQueue) {
                futureTaskQueue.forEach { it.run() }
            }
        }
        endTesting()
    }

    @Mod.EventHandler
    fun serverStopped(event: FMLServerStoppedEvent) {
        finishedLatch.countDown()
    }

    // Clear all worlds and shut down the server
    private fun endTesting() {
        server.worldServers = null
        server.initiateShutdown()
    }

    // Set world spawn to origin and add a loaded chunk so the world ticks without needing a player
    @SubscribeEvent
    fun onWorldLoad(event: WorldEvent.Load) {
        event.world.spawnPoint = BlockPos.ORIGIN

        ForgeChunkManager.setForcedChunkLoadingCallback(this, { _, _ -> })
        val ticket = ForgeChunkManager.requestTicket(this, event.world, ForgeChunkManager.Type.NORMAL)
        ForgeChunkManager.forceChunk(ticket, ChunkPos(BlockPos.ORIGIN))
    }
}
