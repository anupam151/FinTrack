package com.cluster.fintrack;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

@SuppressWarnings("unused") // Hides the "never used" warning until we connect it in the next step!
public class FinMateAdapter extends RecyclerView.Adapter<FinMateAdapter.FinMateViewHolder> {

    // Added the 'final' keyword here to fix the warnings and improve performance!
    private final Context context;
    private final List<FinMate> finMateList;

    public FinMateAdapter(Context context, List<FinMate> finMateList) {
        this.context = context;
        this.finMateList = finMateList;
    }

    @NonNull
    @Override
    public FinMateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_dashboard_finmate, parent, false);
        return new FinMateViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FinMateViewHolder holder, int position) {
        FinMate finmate = finMateList.get(position);

        holder.tvFinmateName.setText(finmate.getName());

        // This line right here calls the magic initials logic!
        holder.tvFinmateInitials.setText(getInitials(finmate.getName()));

        holder.tvFinmateReceivable.setText("₹0");
        holder.tvFinmatePayable.setText("₹0");

        if (finmate.getWhatsappNo() == null || finmate.getWhatsappNo().isEmpty()) {
            holder.ivWhatsApp.setVisibility(View.GONE);
        } else {
            holder.ivWhatsApp.setVisibility(View.VISIBLE);

            holder.ivWhatsApp.setOnClickListener(v -> {
                String url = "https://api.whatsapp.com/send?phone=+91" + finmate.getWhatsappNo();
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));
                context.startActivity(intent);
            });
        }

        holder.itemView.setOnClickListener(v -> Toast.makeText(context, "Clicked: " + finmate.getName(), Toast.LENGTH_SHORT).show());
    }

    @Override
    public int getItemCount() {
        return finMateList.size();
    }

    // --- THIS IS THE MAGIC LOGIC THAT TURNS "Anupam Das" INTO "AD" ---
    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] words = name.trim().split("\\s+"); // splits by space
        if (words.length == 1) {
            return String.valueOf(words[0].charAt(0)).toUpperCase();
        } else {
            return (String.valueOf(words[0].charAt(0)) + words[words.length - 1].charAt(0)).toUpperCase();
        }
    }

    public static class FinMateViewHolder extends RecyclerView.ViewHolder {
        TextView tvFinmateName, tvFinmateReceivable, tvFinmatePayable, tvFinmateInitials;
        ImageView ivWhatsApp;

        public FinMateViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFinmateName = itemView.findViewById(R.id.tvFinmateName);
            tvFinmateReceivable = itemView.findViewById(R.id.tvFinmateReceivable);
            tvFinmatePayable = itemView.findViewById(R.id.tvFinmatePayable);
            tvFinmateInitials = itemView.findViewById(R.id.tvFinmateInitials);
            ivWhatsApp = itemView.findViewById(R.id.ivWhatsApp);
        }
    }
}