package com.tarpha.torrssen2.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.tarpha.torrssen2.domain.DownloadList;
import com.tarpha.torrssen2.domain.WatchList;
import com.tarpha.torrssen2.repository.DownloadListRepository;
import com.tarpha.torrssen2.repository.WatchListRepository;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DownloadService {

    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private DownloadListRepository downloadListRepository;

    @Autowired
    private WatchListRepository watchListRepository;

    @Autowired
    private SettingService settingService;

    @Autowired
    private DownloadStationService downloadStationService;

    @Autowired
    private FileStationService fileStationService;

    @Autowired
    private TransmissionService transmissionService;

    @Autowired
    private HttpDownloadService httpDownloadService;

    @Autowired
    private BtService btService;

    public DownloadList getInfo(long id) {
        String app = settingService.getDownloadApp();
        if(StringUtils.equals(app, "DOWNLOAD_STATION")) {
            Optional<DownloadList> down = downloadListRepository.findById(id);
            if(down.isPresent()) {
                return downloadStationService.getInfo(down.get().getDbid());
            }
        } else if(StringUtils.equals(app, "TRANSMISSION")) {
            List<Long> ids = new ArrayList<Long>();
            ids.add(id);
            List<DownloadList> list = transmissionService.torrentGet(ids);
            if(list.size() > 0) {
                return list.get(0);
            } else {
                return httpDownloadService.getInfo(id);
            }
        } else if(StringUtils.equals(app, "EMBEDDED")) {
            btService.check();
            return btService.getInfo(id);
        }

        return null;
    }

    public List<DownloadList> list() {
        List<DownloadList> ret = null;
        logger.debug("downloadList");

        String app = settingService.getDownloadApp();
        if (StringUtils.equals(app, "DOWNLOAD_STATION")) {
            ret = downloadStationService.list();
        } else if (StringUtils.equals(app, "TRANSMISSION")) {
            ret = transmissionService.torrentGet(null);
        } else if(StringUtils.equals(app, "EMBEDDED")) {
            btService.check();
            ret = btService.list();
        }

        return ret;
    }

    public long create(DownloadList download) {
        long ret = 0L;

        String app = settingService.getDownloadApp();
        if(StringUtils.equals(app, "DOWNLOAD_STATION")) {
            String[] paths = StringUtils.split(download.getDownloadPath(), "/");

            if(paths.length > 1) {
                StringBuffer path = new StringBuffer();
                String name = null;
                for(int i = 0; i < paths.length; i++) {
                    if(i < paths.length -1) {
                        path.append("/" + paths[i]);
                    } else {
                        name = paths[i];
                    }
                }
                fileStationService.createFolder(path.toString(), name);
            }
            if(downloadStationService.create(download.getUri(), download.getDownloadPath())) {
                for(DownloadList down: downloadStationService.list()) {
                    if(StringUtils.equals(download.getUri(), down.getUri())) {
                        ret = down.getId();
                        download.setDbid(down.getDbid());
                    }
                }
            }
        } else if(StringUtils.equals(app, "TRANSMISSION")) {
            if (StringUtils.startsWith(download.getUri(), "magnet")
                || StringUtils.equalsIgnoreCase(FilenameUtils.getExtension(download.getUri()), "torrent")) {
                ret = (long)transmissionService.torrentAdd(download.getUri(), download.getDownloadPath());
            } else {
                Optional<DownloadList> optionalSeq = downloadListRepository.findTopByOrderByIdDesc();
                if (optionalSeq.isPresent()) {
                    Long id = optionalSeq.get().getId() + 100L;                    
                    logger.debug("id: " + id);
                    ret = id;
                } else {
                    ret = 100L;
                }
                download.setId(ret);
                httpDownloadService.createTransmission(download);
            }
            
        } else if(StringUtils.equals(app, "EMBEDDED")) {
            ret = btService.create(download.getUri(), download.getDownloadPath(), download.getName());
        }

        if(ret > 0L) {
            download.setId(ret);
            downloadListRepository.save(download);
        }

        if(download.getAuto()) {
            WatchList watchList = new WatchList();
            watchList.setTitle(download.getRssTitle());
            watchList.setDownloadPath(download.getDownloadPath());
            if(!StringUtils.equals(download.getRssReleaseGroup(), "OTHERS")) {
                watchList.setReleaseGroup(download.getRssReleaseGroup());
            }
            
            watchListRepository.save(watchList);
        }

        return ret;
    }

    public int remove(DownloadList download) {
        int ret = -1;
        boolean res = false;

        String app = settingService.getDownloadApp();
        if(StringUtils.equals(app, "DOWNLOAD_STATION")) {
            List<String> ids = new ArrayList<>();
            ids.add(downloadStationService.getDbId(download.getId()));
            res = downloadStationService.delete(ids);
        } else if(StringUtils.equals(app, "TRANSMISSION")) {
            List<Long> ids = new ArrayList<>();
            ids.add(download.getId());
            res = transmissionService.torrentRemove(ids);
        } else if(StringUtils.equals(app, "EMBEDDED")) {
            res = btService.remove(download.getId());
        }

        if(res) {
            Optional<DownloadList> down = downloadListRepository.findById(download.getId());
            if(down.isPresent()) {
                try {
                    DownloadList temp = down.get();
                    temp.setCancel(true);
                    downloadListRepository.save(temp);
                    ret = temp.getVueItemIndex();
                } catch (NullPointerException e) {
                    logger.error(e.getMessage());
                }
            } else {
                ret = -2;
            }
        }

        return ret;
    }

    public boolean connTest(String app, String host, String port, String id, String pwd) {
        if(StringUtils.equals(app, "dsTest")) {
            return downloadStationService.test(host, port, id, pwd);
        } else if(StringUtils.equals(app, "transmissionTest")) {
            return transmissionService.test(host, port, id, pwd);
        }
        return false;
    }
}