package com.mojang.launcher.game.process.direct;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.EvictingQueue;
import com.mojang.launcher.events.GameOutputLogProcessor;
import com.mojang.launcher.game.process.AbstractGameProcess;
import com.mojang.launcher.game.process.GameProcessRunnable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DirectGameProcess
extends AbstractGameProcess {
    private final Process process;

    public DirectGameProcess(List<String> commands, Process process, Predicate<String> sysOutFilter, GameOutputLogProcessor logProcessor) {
        super(commands, sysOutFilter);
        this.process = process;
        // Thread minimalista: bloqueia em waitFor() e notifica quando o jogo encerra.
        // Nenhuma leitura de pipe — zero overhead no launcher durante o gameplay.
        Thread waiter = new Thread("mc-process-watcher") {
            @Override
            public void run() {
                try {
                    process.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                GameProcessRunnable onExit = DirectGameProcess.this.getExitRunnable();
                if (onExit != null) {
                    onExit.onGameProcessEnded(DirectGameProcess.this);
                }
            }
        };
        waiter.setDaemon(true);
        waiter.setPriority(Thread.MIN_PRIORITY);
        waiter.start();
    }

    public Process getRawProcess() {
        return this.process;
    }

    @Override
    public Collection<String> getSysOutLines() {
        // Saída descartada no nível do SO — sempre vazio.
        return Collections.emptyList();
    }

    @Override
    public boolean isRunning() {
        try {
            this.process.exitValue();
        } catch (IllegalThreadStateException ex) {
            return true;
        }
        return false;
    }

    @Override
    public int getExitCode() {
        try {
            return this.process.waitFor();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            try {
                return this.process.exitValue();
            } catch (IllegalThreadStateException itse) {
                return -1;
            }
        }
    }

    @Override
    public void stop() {
        this.process.destroy();
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("process", this.process).toString();
    }
}
