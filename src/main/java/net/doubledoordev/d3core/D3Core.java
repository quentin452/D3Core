/*
 * Copyright (c) 2014,
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the {organization} nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *
 */

package net.doubledoordev.d3core;

import cpw.mods.fml.client.config.IConfigElement;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.LanguageRegistry;
import cpw.mods.fml.common.versioning.ArtifactVersion;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import net.doubledoordev.d3core.util.CoreHelper;
import net.doubledoordev.d3core.util.DevPerks;
import net.doubledoordev.d3core.util.ID3Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;

import static net.doubledoordev.d3core.util.CoreConstants.*;

/**
 * @author Dries007
 */
@Mod(modid = MODID, name = NAME, canBeDeactivated = false, guiFactory = MOD_GUI_FACTORY)
public class D3Core implements ID3Mod
{
    @Mod.Instance(MODID)
    public static D3Core instance;

    @Mod.Metadata
    private ModMetadata metadata;

    private Logger logger;
    private DevPerks devPerks;
    private Configuration configuration;

    private boolean debug = false;
    private boolean sillyness = true;

    private List<CoreHelper.ModUpdateDate> updateDateList = new ArrayList<>();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        FMLCommonHandler.instance().bus().register(this);

        logger = event.getModLog();
        configuration = new Configuration(event.getSuggestedConfigurationFile());
        syncConfig();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event)
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        for (ModContainer modContainer : Loader.instance().getActiveModList())
        {
            try
            {
                if (modContainer instanceof FMLModContainer && modContainer.getMod() instanceof ID3Mod)
                {
                    if (debug()) logger.info(String.format("[%s] Found a D3 Mod!", modContainer.getModId()));
                    Properties properties = ((FMLModContainer) modContainer).searchForVersionProperties();
                    if (properties != null && properties.containsKey(modContainer.getModId() + ".group") && properties.containsKey(modContainer.getModId() + ".artifactId"))
                    {
                        TreeSet<ArtifactVersion> availableVersions = new TreeSet<>();

                        String group = (String) properties.get(modContainer.getModId() + ".group");
                        String artifactId = (String) properties.get(modContainer.getModId() + ".artifactId");
                        if (debug()) logger.info(String.format("[%s] Group: %s ArtifactId: %s", modContainer.getModId(), group, artifactId));

                        URL url = new URL(MAVENURL + group.replace('.', '/') + '/' + artifactId + "/maven-metadata.xml");
                        if (debug()) logger.info(String.format("[%s] Maven URL: %s", modContainer.getModId(), url));

                        DocumentBuilder builder = dbf.newDocumentBuilder();
                        Document document = builder.parse(url.toURI().toString());
                        NodeList list = document.getDocumentElement().getElementsByTagName("version");
                        for (int i = 0; i < list.getLength(); i++)
                        {
                            String version = list.item(i).getFirstChild().getNodeValue();
                            if (version.startsWith(Loader.MC_VERSION + "-"))
                            {
                                availableVersions.add(new DefaultArtifactVersion(version.replace(Loader.MC_VERSION + "-", "")));
                            }
                        }
                        DefaultArtifactVersion current = new DefaultArtifactVersion(modContainer.getVersion().replace(Loader.MC_VERSION + "-", ""));

                        if (debug()) logger.info(String.format("[%s] Current: %s Latest: %s All versions for MC %s: %s", modContainer.getModId(), current, availableVersions.last(), Loader.MC_VERSION, availableVersions));

                        if (current.compareTo(availableVersions.last()) > 0)
                        {
                            updateDateList.add(new CoreHelper.ModUpdateDate(modContainer.getName(), modContainer.getModId(), current.toString(), availableVersions.last().toString()));
                        }
                    }
                    else
                    {
                        logger.info("D3 Mod " + modContainer.getModId() + " doesn't have the appropriate properties for version checks.");
                    }
                }
            }
            catch (Exception e)
            {
                logger.info("D3 Mod " + modContainer.getModId() + " Version check FAILED. Please report this error!");
                e.printStackTrace();
            }
        }
    }

    @Mod.EventHandler
    public void init(FMLPostInitializationEvent event)
    {
        for (CoreHelper.ModUpdateDate updateDate : updateDateList)
        {
            logger.warn(String.format("Update available for %s (%s)! Current version: %s New version: %s. Please update ASAP!", updateDate.getName(), updateDate.getModId(), updateDate.getCurrentVersion(), updateDate.getLatestVersion()));
        }
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent eventArgs)
    {
        for (ModContainer modContainer : Loader.instance().getActiveModList())
        {
            if (modContainer.getMod() instanceof ID3Mod)
            {
                ((ID3Mod) modContainer.getMod()).syncConfig();
            }
        }
    }

    @Override
    public void syncConfig()
    {
        configuration.setCategoryLanguageKey(MODID, "d3.core.config.core").setCategoryComment(MODID, LanguageRegistry.instance().getStringLocalization("d3.core.config.core"));

        debug = configuration.getBoolean("debug", MODID, debug, "Enable debug mode", "d3.core.config.debug");
        sillyness = configuration.getBoolean("sillyness", MODID, sillyness, "Enable sillyness", "d3.core.config.sillyness");

        if (sillyness) MinecraftForge.EVENT_BUS.register(getDevPerks());
        else MinecraftForge.EVENT_BUS.unregister(getDevPerks());

        if (configuration.hasChanged()) configuration.save();
    }

    @Override
    public void addConfigElements(List<IConfigElement> list)
    {
        list.add(new ConfigElement(configuration.getCategory(MODID.toLowerCase())));
    }

    public static Logger getLogger()
    {
        return instance.logger;
    }

    public static boolean debug()
    {
        return instance.debug;
    }

    public static Configuration getConfiguration()
    {
        return instance.configuration;
    }

    public static DevPerks getDevPerks()
    {
        if (instance.devPerks == null) instance.devPerks = new DevPerks();
        return instance.devPerks;
    }
}
