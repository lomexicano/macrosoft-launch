package com.mojang.launcher.game.process.direct;

import com.google.common.base.Predicate;
import com.mojang.launcher.events.GameOutputLogProcessor;
import com.mojang.launcher.game.process.GameProcess;
import com.mojang.launcher.game.process.GameProcessRunnable;
import com.mojang.launcher.game.process.direct.DirectGameProcess;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DirectProcessInputMonitor
extends Thread {
    private static final Logger LOGGER = LogManager.getLogger();
    private final DirectGameProcess process;
    private final GameOutputLogProcessor logProcessor;

    public DirectProcessInputMonitor(DirectGameProcess process, GameOutputLogProcessor logProcessor) {
        this.process = process;
        this.logProcessor = logProcessor;
        // Thread de baixa prioridade: não deve competir com o processo do jogo pelo scheduler do SO.
        this.setDaemon(true);
        this.setPriority(Thread.MIN_PRIORITY);
    }

    @Override
    public void run() {
        // Lê o stdout do processo de forma bloqueante (readLine() bloqueia até uma linha
        // estar disponível ou o stream fechar — não é necessário polling de isRunning()).
        // Buffer grande (64 KB) para reduzir syscalls quando o jogo é verbose.
        InputStreamReader reader = new InputStreamReader(this.process.getRawProcess().getInputStream());
        BufferedReader buf = new BufferedReader(reader, 65536);
        try {
            String line;
            while ((line = buf.readLine()) != null) {
                this.logProcessor.onGameOutput(this.process, line);
                if (this.process.getSysOutFilter().apply(line)) {
                    this.process.getSysOutLines().add(line);
                }
            }
        } catch (IOException ex) {
            LOGGER.error("Erro ao ler saída do processo do jogo", ex);
        } finally {
            IOUtils.closeQuietly(buf);
            IOUtils.closeQuietly(reader);
        }
        // Aguarda o processo encerrar de verdade antes de notificar.
        // O stdout pode fechar (EOF) um instante antes do processo sair,
        // o que faria getExitCode() lançar IllegalThreadStateException se chamado cedo demais.
        try {
            this.process.getRawProcess().waitFor();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Monitor interrompido aguardando término do processo", ex);
        }
        GameProcessRunnable onExit = this.process.getExitRunnable();
        if (onExit != null) {
            onExit.onGameProcessEnded(this.process);
        }
    }
}

