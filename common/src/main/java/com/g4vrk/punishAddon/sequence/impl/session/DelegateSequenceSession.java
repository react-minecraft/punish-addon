package com.g4vrk.punishAddon.sequence.impl.session;

import com.g4vrk.punishAddon.sequence.SequenceSession;
import org.jetbrains.annotations.NotNull;

public class DelegateSequenceSession implements SequenceSession {

    private final Runnable start;

    public DelegateSequenceSession(
            final @NotNull Runnable start
    ) {

        this.start = start;

    }

    @Override
    public void start() {

        this.start.run();

    }

}
