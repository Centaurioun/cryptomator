/*******************************************************************************
 * Copyright (c) 2017 Skymatic UG (haftungsbeschränkt).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the accompanying LICENSE file.
 *******************************************************************************/
package org.cryptomator.common.settings;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.lang3.SystemUtils;
import org.cryptomator.common.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javafx.geometry.NodeOrientation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class SettingsJsonAdapter extends TypeAdapter<Settings> {

	private static final Logger LOG = LoggerFactory.getLogger(SettingsJsonAdapter.class);

	private final VaultSettingsJsonAdapter vaultSettingsJsonAdapter = new VaultSettingsJsonAdapter();
	private final Environment env;

	@Inject
	public SettingsJsonAdapter(Environment env) {
		this.env = env;
	}

	@Override
	public void write(JsonWriter out, Settings value) throws IOException {
		out.beginObject();
		out.name("writtenByVersion").value(env.getAppVersion() + env.getBuildNumber().map("-"::concat).orElse(""));
		out.name("directories");
		writeVaultSettingsArray(out, value.getDirectories());
		out.name("askedForUpdateCheck").value(value.askedForUpdateCheck().get());
		out.name("autoCloseVaults").value(value.autoCloseVaults().get());
		out.name("checkForUpdatesEnabled").value(value.checkForUpdates().get());
		out.name("debugMode").value(value.debugMode().get());
		out.name("displayConfiguration").value((value.displayConfigurationProperty().get()));
		out.name("keychainProvider").value(value.keychainProvider().get());
		out.name("language").value((value.languageProperty().get()));
		out.name("licenseKey").value(value.licenseKey().get());
		out.name("mountService").value(value.mountService().get());
		out.name("numTrayNotifications").value(value.numTrayNotifications().get());
		out.name("port").value(value.port().get());
		out.name("showMinimizeButton").value(value.showMinimizeButton().get());
		out.name("showTrayIcon").value(value.showTrayIcon().get());
		out.name("startHidden").value(value.startHidden().get());
		out.name("theme").value(value.theme().get().name());
		out.name("uiOrientation").value(value.userInterfaceOrientation().get().name());
		out.name("useKeychain").value(value.useKeychain().get());
		out.name("windowHeight").value((value.windowHeightProperty().get()));
		out.name("windowWidth").value((value.windowWidthProperty().get()));
		out.name("windowXPosition").value((value.windowXPositionProperty().get()));
		out.name("windowYPosition").value((value.windowYPositionProperty().get()));
		out.endObject();
	}

	private void writeVaultSettingsArray(JsonWriter out, Iterable<VaultSettings> vaultSettings) throws IOException {
		out.beginArray();
		for (VaultSettings value : vaultSettings) {
			vaultSettingsJsonAdapter.write(out, value);
		}
		out.endArray();
	}

	@Override
	public Settings read(JsonReader in) throws IOException {
		Settings settings = new Settings(env);
		//1.6.x legacy
		String volumeImpl = null;
		//legacy end
		in.beginObject();
		while (in.hasNext()) {
			String name = in.nextName();
			switch (name) {
				case "writtenByVersion" -> in.skipValue(); //noop
				case "directories" -> settings.getDirectories().addAll(readVaultSettingsArray(in));
				case "askedForUpdateCheck" -> settings.askedForUpdateCheck().set(in.nextBoolean());
				case "autoCloseVaults" -> settings.autoCloseVaults().set(in.nextBoolean());
				case "checkForUpdatesEnabled" -> settings.checkForUpdates().set(in.nextBoolean());
				case "debugMode" -> settings.debugMode().set(in.nextBoolean());
				case "displayConfiguration" -> settings.displayConfigurationProperty().set(in.nextString());
				case "keychainProvider" -> settings.keychainProvider().set(in.nextString());
				case "language" -> settings.languageProperty().set(in.nextString());
				case "licenseKey" -> settings.licenseKey().set(in.nextString());
				case "mountService" -> {
					var token = in.peek();
					if (JsonToken.STRING == token) {
						settings.mountService().set(in.nextString());
					}
				}
				case "numTrayNotifications" -> settings.numTrayNotifications().set(in.nextInt());
				case "port" -> settings.port().set(in.nextInt());
				case "showMinimizeButton" -> settings.showMinimizeButton().set(in.nextBoolean());
				case "showTrayIcon" -> settings.showTrayIcon().set(in.nextBoolean());
				case "startHidden" -> settings.startHidden().set(in.nextBoolean());
				case "theme" -> settings.theme().set(parseUiTheme(in.nextString()));
				case "uiOrientation" -> settings.userInterfaceOrientation().set(parseUiOrientation(in.nextString()));
				case "useKeychain" -> settings.useKeychain().set(in.nextBoolean());
				case "windowHeight" -> settings.windowHeightProperty().set(in.nextInt());
				case "windowWidth" -> settings.windowWidthProperty().set(in.nextInt());
				case "windowXPosition" -> settings.windowXPositionProperty().set(in.nextInt());
				case "windowYPosition" -> settings.windowYPositionProperty().set(in.nextInt());
				//1.6.x legacy
				case "preferredVolumeImpl" -> volumeImpl = in.nextString();
				//legacy end
				default -> {
					LOG.warn("Unsupported vault setting found in JSON: {}", name);
					in.skipValue();
				}
			}

		}
		in.endObject();

		//1.6.x legacy
		if (volumeImpl != null) {
			settings.mountService().set(convertLegacyVolumeImplToMountService(volumeImpl));
		}
		//legacy end

		return settings;
	}

	private String convertLegacyVolumeImplToMountService(String volumeImpl) {
		if (volumeImpl.equals("Dokany")) {
			return "org.cryptomator.frontend.dokany.mount.DokanyMountProvider";
		} else if (volumeImpl.equals("FUSE")) {
			if (SystemUtils.IS_OS_WINDOWS) {
				return "org.cryptomator.frontend.fuse.mount.WinFspNetworkMountProvider";
			} else if (SystemUtils.IS_OS_MAC) {
				return "org.cryptomator.frontend.fuse.mount.MacFuseMountProvider";
			} else {
				return "org.cryptomator.frontend.fuse.mount.LinuxFuseMountProvider";
			}
		} else {
			if (SystemUtils.IS_OS_WINDOWS) {
				return "org.cryptomator.frontend.webdav.mount.WindowsMounter";
			} else if (SystemUtils.IS_OS_MAC) {
				return "org.cryptomator.frontend.webdav.mount.MacAppleScriptMounter";
			} else {
				return "org.cryptomator.frontend.webdav.mount.LinuxGioMounter";
			}
		}
	}

	private UiTheme parseUiTheme(String uiThemeName) {
		try {
			return UiTheme.valueOf(uiThemeName.toUpperCase());
		} catch (IllegalArgumentException e) {
			LOG.warn("Invalid ui theme {}. Defaulting to {}.", uiThemeName, Settings.DEFAULT_THEME);
			return Settings.DEFAULT_THEME;
		}
	}

	private NodeOrientation parseUiOrientation(String uiOrientationName) {
		try {
			return NodeOrientation.valueOf(uiOrientationName.toUpperCase());
		} catch (IllegalArgumentException e) {
			LOG.warn("Invalid ui orientation {}. Defaulting to {}.", uiOrientationName, Settings.DEFAULT_USER_INTERFACE_ORIENTATION);
			return Settings.DEFAULT_USER_INTERFACE_ORIENTATION;
		}
	}

	private List<VaultSettings> readVaultSettingsArray(JsonReader in) throws IOException {
		List<VaultSettings> result = new ArrayList<>();
		in.beginArray();
		while (!JsonToken.END_ARRAY.equals(in.peek())) {
			result.add(vaultSettingsJsonAdapter.read(in));
		}
		in.endArray();
		return result;
	}
}