package com.mojang.launcher.game.process.direct;

import com.google.common.base.Predicate;
import com.mojang.launcher.events.GameOutputLogProcessor;
import com.mojang.launcher.game.process.GameProcess;
import com.mojang.launcher.game.process.GameProcessBuilder;
import com.mojang.launcher.game.process.GameProcessFactory;
import com.mojang.launcher.game.process.direct.DirectGameProcess;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class DirectGameProcessFactory
implements GameProcessFactory {
    @Override
    public GameProcess startGame(GameProcessBuilder builder) throws IOException {
    	
    	/*
    	 * Path absolutePath = Paths.get(javaDir);
    	if (!Paths.get(javaDir).isAbsolute()) {
    		Path currentDir = Paths.get("").toAbsolutePath();
            absolutePath = currentDir.resolve(javaDir).normalize();
    	}
        
    	this.javaDir = absolutePath.toString();
        
    	 */
        List<String> full = builder.getFullCommands();
        // Redirecionar stdout e stderr do jogo para DISCARD (equivalente a /dev/null).
        // Os writes do Minecraft completam instantaneamente sem pipe, sem thread de leitura,
        // sem nenhum overhead no launcher durante o gameplay.
        Process process = new ProcessBuilder(full)
                .directory(builder.getDirectory())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        return new DirectGameProcess(full, process, builder.getSysOutFilter(), builder.getLogProcessor());
    }
}

