package com.tulas.canteen.domain;

public enum OrderStatus {
    PLACED, PREPARING, READY, COLLECTED, CANCELLED, NO_SHOW;

    public boolean isActive() {
        return this == PLACED || this == PREPARING || this == READY;
    }
}
