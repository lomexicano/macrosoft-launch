package net.minecraft.launcher.ui.popups.login;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.ArrayUtils;

import java.util.HashMap;

import com.mojang.authlib.AuthenticationService;
import com.mojang.authlib.BaseUserAuthentication;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.UserAuthentication;
import com.mojang.authlib.UserType;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.yggdrasil.request.RefreshRequest;
import com.mojang.authlib.yggdrasil.response.RefreshResponse;

public class MacrosoftMockAuth
extends BaseUserAuthentication {
	
	private GameProfile[] profiles;

	protected MacrosoftMockAuth(AuthenticationService authenticationService) {
		super(authenticationService);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void logIn() throws AuthenticationException {
		 UUID uuid = UUID.randomUUID();
		 GameProfile profile = new GameProfile(uuid, this.getUsername());
		 this.setSelectedProfile(profile);
	}

	@Override
	public boolean canPlayOnline() {
		return true;
	}

	@Override
	public GameProfile[] getAvailableProfiles() {
		return this.profiles;
	}

	@Override
	public void selectGameProfile(GameProfile var1) throws AuthenticationException {
        this.setSelectedProfile(var1);
	}

	@Override
	public String getAuthenticatedToken() {
		// TODO Auto-generated method stub
		return null;
	}
	
}
