package com.tulas.canteen.domain;

/** OPEN takes orders, BUSY takes orders with a longer wait warning, CLOSED rejects new orders. */
public enum CanteenStatus { OPEN, BUSY, CLOSED }
