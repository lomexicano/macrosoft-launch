// ===== ./src/main/java/net/minecraft/launcher/utils/CryptoUtils.java =====
package net.minecraft.launcher.utils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.mojang.launcher.OperatingSystem;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CryptoUtils {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128; // bits
    private static final int IV_LENGTH_BYTE = 12; // bytes
    private static final int SALT_LENGTH_BYTE = 16; // bytes
    private static final String SECRET_FILE_NAME = ".launcher_secret";
    private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int KEY_ITERATION_COUNT = 65536;
    private static final int KEY_LENGTH_BIT = 256; // bits

    private static SecretKey machineKey = null; // Cache da chave derivada da máquina

    // Método para obter o diretório de dados do aplicativo de forma segura
    private static Path getAppDataDirectory(net.minecraft.launcher.Launcher launcherInstance) {
        // Usar o diretório de trabalho principal do launcher, que já é específico do OS
        return launcherInstance.getLauncher().getWorkingDirectory().toPath().getParent(); // Pegar o pai do ".macrosoft/<context>"
    }

    private static String getMachineSecret(net.minecraft.launcher.Launcher launcherInstance) throws IOException {
        Path secretFilePath = getAppDataDirectory(launcherInstance).resolve(SECRET_FILE_NAME);
        String secret;

        if (Files.exists(secretFilePath)) {
            secret = new String(Files.readAllBytes(secretFilePath), StandardCharsets.UTF_8);
        } else {
            secret = UUID.randomUUID().toString() + UUID.randomUUID().toString(); // Tornar mais longo
            Files.write(secretFilePath, secret.getBytes(StandardCharsets.UTF_8));
            try {
                if (OperatingSystem.getCurrentPlatform() != OperatingSystem.WINDOWS) {
                    Set<PosixFilePermission> perms = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
                    Files.setPosixFilePermissions(secretFilePath, perms);
                } else {
                    // No Windows, as permissões de arquivo são mais complexas de definir via Java puro
                    // A ACL padrão geralmente restringe ao usuário e administradores.
                    // Para maior segurança, seria necessário JNA/JNI ou comandos `icacls`.
                    // Por simplicidade, vamos assumir que o local é razoavelmente seguro.
                }
            } catch (IOException e) {
                LOGGER.warn("Não foi possível definir permissões restritas para o arquivo de segredo: " + secretFilePath, e);
            } catch (UnsupportedOperationException e) {
                LOGGER.warn("Definição de permissões POSIX não suportada neste sistema para: " + secretFilePath);
            }
        }
        return secret;
    }

    private static SecretKey getDerivedMachineKey(net.minecraft.launcher.Launcher launcherInstance, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        // Se já tivermos a chave em cache E o salt for o mesmo (improvável para derivação com salt por senha)
        // Para este caso, o "machineSecret" é fixo, então o salt da senha criptografada é o que varia.
        // A chave derivada do machineSecret + salt_da_senha_específica é o que precisamos.

        String machinePassword = getMachineSecret(launcherInstance);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM);
        KeySpec spec = new PBEKeySpec(machinePassword.toCharArray(), salt, KEY_ITERATION_COUNT, KEY_LENGTH_BIT);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }


    public static String encrypt(String strToEncrypt, net.minecraft.launcher.Launcher launcherInstance) {
        if (strToEncrypt == null) return null;
        try {
            byte[] salt = new byte[SALT_LENGTH_BYTE];
            SecureRandom sr = new SecureRandom();
            sr.nextBytes(salt);

            byte[] iv = new byte[IV_LENGTH_BYTE];
            sr.nextBytes(iv);

            SecretKey secretKey = getDerivedMachineKey(launcherInstance, salt);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8));

            // Prepend salt and IV to the ciphertext for storage
            // Formato: base64(salt):base64(iv):base64(ciphertext)
            return Base64.getEncoder().encodeToString(salt) + ":" +
                   Base64.getEncoder().encodeToString(iv) + ":" +
                   Base64.getEncoder().encodeToString(cipherText);

        } catch (Exception e) {
            LOGGER.error("Erro ao criptografar: ", e);
        }
        return null; // ou lançar uma exceção específica
    }

    public static String decrypt(String strToDecrypt, net.minecraft.launcher.Launcher launcherInstance) {
        if (strToDecrypt == null) return null;
        try {
            String[] parts = strToDecrypt.split(":");
            if (parts.length != 3) {
                LOGGER.error("Formato de senha criptografada inválido. Esperava 3 partes, obteve " + parts.length);
                // Tentar tratar como senha em texto plano por compatibilidade legada?
                // Ou simplesmente falhar. Por segurança, falhar é melhor.
                // Se for uma senha que não foi criptografada ainda, esta lógica falhará.
                // Poderia retornar strToDecrypt se não contiver ":" para tentar compatibilidade.
                if (!strToDecrypt.contains(":")) { // Suposição muito simples de que senhas antigas não têm ":"
                    LOGGER.warn("A senha parece não estar criptografada (sem separador ':'). Tentando usá-la como texto plano. Isso é inseguro.");
                    return strToDecrypt;
                }
                return null; // Falha na decriptografia
            }

            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] cipherText = Base64.getDecoder().decode(parts[2]);

            SecretKey secretKey = getDerivedMachineKey(launcherInstance, salt);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);

        } catch (Exception e) {
            LOGGER.error("Erro ao descriptografar (a senha pode estar em formato antigo ou a chave/SO mudou): " + e.getMessage());
            // Não logar e.toString() completo pois pode vazar informações se for um IllegalBlockSizeException em texto plano
            // Se a senha não foi criptografada (é texto plano) e o split falhou ou a decodificação base64 falhou,
            // ou a descriptografia falhou porque não era AES/GCM.
            // Se strToDecrypt não contiver ":", pode ser uma senha antiga em texto plano.
             if (!strToDecrypt.contains(":")) {
                LOGGER.warn("Tentando usar a 'senha criptografada' como texto plano devido a falha na descriptografia e ausência de separador ':'.");
                return strToDecrypt;
            }
        }
        return null; // Falha na decriptografia
    }

    // Para teste simples
    public static void main(String[] args) {
        // Simular uma instância do Launcher para teste.
        // Em um cenário real, você obteria a instância real do Launcher.
        // Este mock é SÓ PARA TESTE DESTA CLASSE ISOLADAMENTE.
        net.minecraft.launcher.Launcher mockLauncher = null;
        try {
            // Para que getAppDataDirectory funcione, precisamos de um working directory.
            // Crie um diretório temporário para o teste.
            Path tempWorkingDirParent = Files.createTempDirectory("launcherTestWD");
            Path tempContextDir = tempWorkingDirParent.resolve(".macrosoft").resolve("testContext");
            Files.createDirectories(tempContextDir);

            // Mocking básico da estrutura esperada por getAppDataDirectory
            com.mojang.launcher.Launcher mojangLauncherMock = new com.mojang.launcher.Launcher(null, tempContextDir.toFile(), null, null, null, null, null, 0);
            mockLauncher = new net.minecraft.launcher.Launcher(new javax.swing.JFrame() /*dummy*/, tempContextDir.toFile(), null, null, new String[]{}, 100);


            System.out.println("Diretório AppData simulado: " + getAppDataDirectory(mockLauncher));
            System.out.println("Machine Secret: " + getMachineSecret(mockLauncher));

            String originalPassword = "MinhaSenhaSuperSecreta!123";
            System.out.println("Senha Original: " + originalPassword);

            String encryptedPassword = CryptoUtils.encrypt(originalPassword, mockLauncher);
            System.out.println("Senha Criptografada: " + encryptedPassword);

            String decryptedPassword = CryptoUtils.decrypt(encryptedPassword, mockLauncher);
            System.out.println("Senha Descriptografada: " + decryptedPassword);

            if (originalPassword.equals(decryptedPassword)) {
                System.out.println("SUCESSO: A senha foi criptografada e descriptografada corretamente.");
            } else {
                System.err.println("FALHA: A senha descriptografada não corresponde à original.");
            }

            // Teste com senha nula
            System.out.println("Criptografando null: " + CryptoUtils.encrypt(null, mockLauncher));
            System.out.println("Descriptografando null: " + CryptoUtils.decrypt(null, mockLauncher));

            // Teste com descriptografia de string inválida (que não foi criptografada)
            String plainTextPass = "senhaantiga";
            System.out.println("Descriptografando senha em texto plano '" + plainTextPass + "': " + CryptoUtils.decrypt(plainTextPass, mockLauncher));

            String invalidEncrypted = "abc:def:ghi"; // Formato certo, mas conteúdo inválido
            System.out.println("Descriptografando formato inválido '" + invalidEncrypted + "': " + CryptoUtils.decrypt(invalidEncrypted, mockLauncher));

             // Limpar arquivo de segredo após o teste
            Files.deleteIfExists(getAppDataDirectory(mockLauncher).resolve(SECRET_FILE_NAME));
            FileUtils.deleteDirectory(tempWorkingDirParent.toFile());


        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}