package com.example.valdker.offline.db.models;

import com.example.valdker.offline.db.entities.OrderEntity;
import com.example.valdker.offline.db.entities.OrderItemEntity;

import java.util.List;

public class OrderWithItems {
    public OrderEntity order;
    public List<OrderItemEntity> items;
}