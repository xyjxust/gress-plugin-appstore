package com.keqi.gress.plugin.appstore.service.node.repository.impl;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import  com.keqi.gress.common.plugin.annotion.Inject;
import  com.keqi.gress.common.plugin.annotion.Service;
import  com.keqi.gress.plugin.api.service.PluginLambdaDataSource;
import com.keqi.gress.plugin.appstore.domain.entity.NodeInfoEntity;
import com.keqi.gress.plugin.appstore.service.node.repository.NodeInfoRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class NodeInfoRepositoryImpl implements NodeInfoRepository {

   // private static final Log log = LogFactory.get(NodeInfoRepositoryImpl.class);

    @Inject(source = Inject.BeanSource.SPRING)
    private PluginLambdaDataSource dataSource;

    @Override
    public void save(NodeInfoEntity entity) {
        if (dataSource == null) {
            throw new IllegalStateException("数据源未配置，无法保存节点信息");
        }
        dataSource.insert(entity);
    }

    @Override
    public void update(NodeInfoEntity entity) {
        if (dataSource == null) {
            throw new IllegalStateException("数据源未配置，无法更新节点信息");
        }
        dataSource.updateById(entity);
    }

    @Override
    public Optional<NodeInfoEntity> findByNodeId(String nodeId) {
        if (dataSource == null) {
            return Optional.empty();
        }
        try {
            NodeInfoEntity entity = dataSource.lambdaQuery(NodeInfoEntity.class)
                .eq(NodeInfoEntity::getNodeId, nodeId)
                .one();
            return Optional.ofNullable(entity);
        } catch (Exception e) {
            log.warn("查询节点失败: nodeId={}, err={}", nodeId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<NodeInfoEntity> findAll() {
        if (dataSource == null) {
            return new ArrayList<>();
        }
        try {
            return dataSource.lambdaQuery(NodeInfoEntity.class)
                .orderByDesc(NodeInfoEntity::getUpdatedAt)
                .list();
        } catch (Exception e) {
            log.warn("查询节点列表失败: err={}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void deleteByNodeId(String nodeId) {
        if (dataSource == null) {
            throw new IllegalStateException("数据源未配置，无法删除节点信息");
        }
        dataSource.lambdaUpdate(NodeInfoEntity.class)
            .eq(NodeInfoEntity::getNodeId, nodeId)
            .delete();
    }
}

