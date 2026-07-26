package com.wjz.mobsthinknow.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 仅由 Mod Menu 在客户端请求的配置入口。
 * 服务端不会加载这个类，因此核心僵尸 AI 仍可在不安装客户端配置依赖时运行。
 */
@Environment(EnvType.CLIENT)
public final class MobsThinkNowModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		// 两个库都只服务于客户端配置界面；缺少 Cloth Config 时让 Mod Menu 隐藏配置按钮。
		if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
			return ModMenuApi.super.getModConfigScreenFactory();
		}

		return MobsThinkNowConfigScreen::create;
	}
}
