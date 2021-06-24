package com.celeste.internal.packets.handlers;

import com.celeste.internal.controller.ChannelController;
import com.celeste.internal.exceptions.PacketException;
import com.celeste.internal.model.impl.PlayerConnection;
import com.celeste.internal.model.type.ConnectionState;
import com.celeste.internal.model.type.LoginState;
import com.celeste.internal.packets.PacketContent;
import com.celeste.internal.packets.PacketHandler;
import com.celeste.internal.packets.messages.login.LoginStartMessage;
import com.celeste.internal.packets.messages.login.LoginSuccessMessage;
import com.celeste.internal.registry.ConnectionRegistry;
import com.celeste.internal.registry.KeepAliveRegistry;
import com.celeste.library.core.util.Logger;
import com.celeste.minecraft.model.Location;
import com.celeste.minecraft.model.type.Gamemode;
import com.mojang.authlib.GameProfile;
import io.grpc.netty.shaded.io.netty.channel.ChannelHandlerContext;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class LoginHandler extends PacketHandler {

  public LoginHandler(final ChannelController controller) {
    super(controller);
    this.loginState = LoginState.START;
  }

  private LoginState loginState;

  private UUID id;
  private String username;

  @Override
  public void read(final ChannelHandlerContext context, final PacketContent message) {
    switch (loginState) {
      case START -> {
        final LoginStartMessage handshake = (LoginStartMessage) message;
        this.username = handshake.getUsername();

        if (getController().isOfflineMode()) {
          Logger.getLogger().atWarning().log("Server is currently at Offline mode. Initiating without Encryption.");

          setLoginState(LoginState.SUCCESS);
          return;
        }

        setLoginState(LoginState.ENCRYPTION_REQUEST);
      }
      case SUCCESS -> {
        // Handle online mode
        if (getController().isOfflineMode()) {
          this.id = UUID.nameUUIDFromBytes(("CelestiumPlayer=" + username).getBytes(StandardCharsets.UTF_8));
        }

        final PlayerConnection playerConnection = PlayerConnection.builder()
            .gameProfile(new GameProfile(id, username))
            .state(ConnectionState.PLAY)
            .address((InetSocketAddress) context.channel().remoteAddress())
            .firstJoin(System.currentTimeMillis())
            .gamemode(Gamemode.SURVIVAL)
            .latency(0)
            .location(new Location())
            .protocolVersion(getController().getProtocolVersion())
            .build();

        ConnectionRegistry.INSTANCE.register(id, playerConnection);

        getController().setState(ConnectionState.PLAY);
        getController().setHandler(new PlayHandler(getController()));
        KeepAliveRegistry.INSTANCE.register(id, new KeepAliveHandler(getController()));

        dispatch(new LoginSuccessMessage(id, username));
      }
      default -> throw new PacketException("A invalid LoginState has been received.");
    }
  }

}
