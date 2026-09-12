package com.valdker.pos.drafts;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "pos_drafts",
        indices = {
                @Index("posType"),
                @Index("isActive")
        }
)
public class PosDraftEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String posType = "retail";
    public String name = "";
    public boolean isActive = false;
    public long createdAt = 0L;
    public long updatedAt = 0L;

    /**
     * Meja dan pelayan dine-in, disimpan PER DRAFT dan bukan di field tingkat
     * Activity. Satu meja boleh punya beberapa bill terbuka, jadi kasir wajar
     * berpindah-pindah antar draft; kalau nilainya ditahan di Activity, dua
     * draft yang dibuka bergantian akan saling menukar mejanya - jebakan yang
     * sama persis dengan yang dulu menukar client_order_id antar draft.
     *
     * <p>{@code null} berarti belum dipilih. Nama disimpan hanya sebagai
     * cache tampilan supaya draft yang dibuka kembali tidak menampilkan
     * "Meja #7" sambil menunggu daftar meja selesai diambil; sumber kebenaran
     * tetap id-nya, dan nama disegarkan setiap kali daftar meja dimuat.
     */
    public Long tableId = null;
    public String tableName = null;
    public Long waiterId = null;
    public String waiterName = null;
}
