package net.minecraft.launcher.utils;

import java.lang.instrument.Instrumentation;
import java.net.Authenticator;
import java.net.PasswordAuthentication;

/**
 * Java Agent que instala um {@link Authenticator} de SOCKS5 no processo filho
 * (Minecraft) antes que a classe principal seja executada.
 *
 * Credenciais são lidas das propriedades de sistema:
 *   -Dnet.minecraft.socks.user=<user>
 *   -Dnet.minecraft.socks.pass=<pass>
 *
 * O launcher injeta esse agente via -javaagent: nos argumentos da JVM filho.
 */
public class SocksAuthenticatorAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        final String user = System.getProperty("net.minecraft.socks.user", "");
        final String pass = System.getProperty("net.minecraft.socks.pass", "");
        if (user != null && !user.isEmpty()) {
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, pass.toCharArray());
                }
            });
        }
    }

    /** Compatibilidade: assinatura sem Instrumentation. */
    public static void premain(String agentArgs) {
        premain(agentArgs, null);
    }
}

