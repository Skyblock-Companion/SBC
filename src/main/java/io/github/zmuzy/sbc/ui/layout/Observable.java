// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), util.Observable, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package io.github.zmuzy.sbc.ui.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

// value that tells its observers when it changes
public class Observable<T> {

    protected T value;
    protected final List<Consumer<T>> observers;

    protected Observable(T initial) {
        this.value = initial;
        this.observers = new ArrayList<>();
    }

    public static <T> Observable<T> of(T initial) {
        return new Observable<>(initial);
    }

    public static void observeAll(Runnable observer, Observable<?>... observables) {
        for (var observable : observables) {
            observable.observe(o -> observer.run());
        }
    }

    public T get() {
        return this.value;
    }

    public void set(T newValue) {
        var oldValue = this.value;
        this.value = newValue;

        if (!Objects.equals(this.value, oldValue)) {
            this.notifyObservers(newValue);
        }
    }

    public void observe(Consumer<T> observer) {
        this.observers.add(observer);
    }

    protected void notifyObservers(T value) {
        for (var observer : this.observers) {
            observer.accept(value);
        }
    }
}
