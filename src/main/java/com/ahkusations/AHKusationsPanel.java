package com.ahkusations;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AHKusationsPanel extends PluginPanel
{
	private static final Color COLOR_LEGITIMATE = new Color(0, 180, 0);
	private static final Color COLOR_SLIGHT = new Color(180, 180, 0);
	private static final Color COLOR_SUSPICIOUS = new Color(255, 165, 0);
	private static final Color COLOR_LIKELY = new Color(255, 80, 0);
	private static final Color COLOR_VERY_LIKELY = new Color(255, 30, 30);
	private static final Color HOVER_COLOR = new Color(60, 60, 60);

	private final AHKusationsPlugin plugin;
	private final CardLayout cardLayout;
	private final JPanel contentPanel;
	private final JPanel fightListContainer;
	private final JPanel detailWrapper;
	private final JLabel statusLabel;

	public AHKusationsPanel(AHKusationsPlugin plugin)
	{
		super(false);
		this.plugin = plugin;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Title bar
		JPanel titlePanel = new JPanel(new BorderLayout());
		titlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		titlePanel.setBorder(new EmptyBorder(8, 10, 8, 10));

		JLabel title = new JLabel("AHKusations");
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
		titlePanel.add(title, BorderLayout.WEST);

		statusLabel = new JLabel("Idle");
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
		titlePanel.add(statusLabel, BorderLayout.EAST);

		add(titlePanel, BorderLayout.NORTH);

		// Card layout for list/detail views
		cardLayout = new CardLayout();
		contentPanel = new JPanel(cardLayout);
		contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// List view
		fightListContainer = new JPanel(new GridBagLayout());
		fightListContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel emptyLabel = new JLabel("No fights recorded yet");
		emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		fightListContainer.add(emptyLabel);

		JScrollPane listScroll = new JScrollPane(fightListContainer);
		listScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		listScroll.getVerticalScrollBar().setUnitIncrement(16);
		listScroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
		listScroll.setBorder(null);

		// Detail view
		detailWrapper = new JPanel(new BorderLayout());
		detailWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);

		contentPanel.add(listScroll, "list");
		contentPanel.add(detailWrapper, "detail");

		add(contentPanel, BorderLayout.CENTER);
		cardLayout.show(contentPanel, "list");

		// Render any fights already loaded from disk
		rebuildFightList();
	}

	public void onFightStarted(String opponentName)
	{
		SwingUtilities.invokeLater(() ->
			statusLabel.setText("Fighting: " + opponentName));
	}

	public void onFightEnded(FightHistoryEntry entry)
	{
		SwingUtilities.invokeLater(() ->
		{
			statusLabel.setText("Idle");
			rebuildFightList();
		});
	}

	private void rebuildFightList()
	{
		fightListContainer.removeAll();

		List<FightHistoryEntry> history = plugin.getFightHistory();
		if (history.isEmpty())
		{
			JLabel emptyLabel = new JLabel("No fights recorded yet");
			emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			fightListContainer.add(emptyLabel);
		}
		else
		{
			GridBagConstraints gbc = new GridBagConstraints();
			gbc.gridx = 0;
			gbc.gridy = 0;
			gbc.weightx = 1;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.insets = new Insets(0, 0, 2, 0);

			for (FightHistoryEntry entry : history)
			{
				fightListContainer.add(createFightCard(entry), gbc);
				gbc.gridy++;
			}

			// Spacer to push cards to the top
			gbc.weighty = 1;
			gbc.fill = GridBagConstraints.BOTH;
			fightListContainer.add(new JLabel(), gbc);
		}

		fightListContainer.revalidate();
		fightListContainer.repaint();
	}

	private JPanel createFightCard(FightHistoryEntry entry)
	{
		CheatAnalysis opp = entry.getOpponentAnalysis();

		JPanel card = new JPanel(new BorderLayout(8, 0));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(6, 8, 6, 8));
		card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		// Left: opponent info
		JPanel infoPanel = new JPanel(new GridLayout(2, 1));
		infoPanel.setOpaque(false);

		String nameText = entry.getOpponentName() + " (lvl " + entry.getOpponentCombatLevel() + ")";
		String userLabel = entry.getUserLabel();
		if ("CHEATER".equals(userLabel))
		{
			nameText += "  [CHEATER]";
		}
		else if ("LEGIT".equals(userLabel))
		{
			nameText += "  [LEGIT]";
		}
		JLabel nameLabel = new JLabel(nameText);
		Color nameColor = "CHEATER".equals(userLabel) ? COLOR_VERY_LIKELY
			: "LEGIT".equals(userLabel) ? COLOR_LEGITIMATE : Color.WHITE;
		nameLabel.setForeground(nameColor);
		nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
		infoPanel.add(nameLabel);

		String timeStr = formatTime(entry.getTimestampMs());
		JLabel metaLabel = new JLabel(timeStr + " | " + String.format("%.1fs", entry.getDurationSeconds())
			+ " | " + entry.getTotalTicks() + " ticks");
		metaLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		metaLabel.setFont(metaLabel.getFont().deriveFont(10f));
		infoPanel.add(metaLabel);

		card.add(infoPanel, BorderLayout.CENTER);

		// Right: score (fixed width so it doesn't push content off)
		if (opp != null)
		{
			JPanel scorePanel = new JPanel(new GridLayout(2, 1));
			scorePanel.setOpaque(false);
			scorePanel.setPreferredSize(new Dimension(55, 36));

			JLabel scoreLabel = new JLabel(String.format("%.0f", opp.getOverallScore()), SwingConstants.RIGHT);
			scoreLabel.setForeground(getScoreColor(opp.getOverallScore()));
			scoreLabel.setFont(scoreLabel.getFont().deriveFont(Font.BOLD, 16f));
			scorePanel.add(scoreLabel);

			JLabel verdictLabel = new JLabel(opp.getVerdict(), SwingConstants.RIGHT);
			verdictLabel.setForeground(getScoreColor(opp.getOverallScore()));
			verdictLabel.setFont(verdictLabel.getFont().deriveFont(8f));
			scorePanel.add(verdictLabel);

			card.add(scorePanel, BorderLayout.EAST);
		}

		card.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				showFightDetail(entry);
			}

			@Override
			public void mouseEntered(java.awt.event.MouseEvent e)
			{
				card.setBackground(HOVER_COLOR);
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent e)
			{
				card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});

		return card;
	}

	private void showFightDetail(FightHistoryEntry entry)
	{
		detailWrapper.removeAll();

		// Use a wrapper that tracks viewport width so children don't overflow
		JPanel content = new JPanel()
		{
			@Override
			public Dimension getPreferredSize()
			{
				Dimension d = super.getPreferredSize();
				// Constrain preferred width to parent's width so BoxLayout children don't overflow
				Container parent = getParent();
				if (parent != null)
				{
					d.width = parent.getWidth();
				}
				return d;
			}
		};
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(new EmptyBorder(8, 8, 8, 8));

		// Back button
		JButton backBtn = new JButton("<< Back to fight list");
		backBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		backBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
		backBtn.addActionListener(e -> cardLayout.show(contentPanel, "list"));
		content.add(backBtn);
		content.add(Box.createRigidArea(new Dimension(0, 10)));

		// Header
		addLeftLabel(content, "vs " + entry.getOpponentName() + " (lvl " + entry.getOpponentCombatLevel() + ")",
			Color.WHITE, Font.BOLD, 14f);

		String timeStr = formatTime(entry.getTimestampMs());
		addLeftLabel(content, timeStr + " | " + String.format("%.1fs", entry.getDurationSeconds())
				+ " | " + entry.getDurationTicks() + " ticks",
			ColorScheme.LIGHT_GRAY_COLOR, Font.PLAIN, 10f);

		content.add(Box.createRigidArea(new Dimension(0, 8)));

		// Label buttons — mark opponent as cheater or legit
		JPanel labelPanel = new JPanel();
		labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.X_AXIS));
		labelPanel.setOpaque(false);
		labelPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		labelPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		JLabel labelTitle = new JLabel("Mark: ");
		labelTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		labelTitle.setFont(labelTitle.getFont().deriveFont(11f));
		labelPanel.add(labelTitle);

		JButton cheaterBtn = new JButton("Cheater");
		JButton legitBtn = new JButton("Legit");
		JButton clearBtn = new JButton("Clear");

		Font smallBtnFont = cheaterBtn.getFont().deriveFont(cheaterBtn.getFont().getSize() * 0.9f);
		cheaterBtn.setFont(smallBtnFont);
		legitBtn.setFont(smallBtnFont);
		clearBtn.setFont(smallBtnFont);
		cheaterBtn.setMargin(new Insets(2, 4, 2, 4));
		legitBtn.setMargin(new Insets(2, 4, 2, 4));
		clearBtn.setMargin(new Insets(2, 4, 2, 4));

		String currentLabel = entry.getUserLabel();
		updateLabelButtons(cheaterBtn, legitBtn, clearBtn, currentLabel);

		cheaterBtn.addActionListener(e -> {
			try { plugin.setFightLabel(entry, "CHEATER"); } catch (Exception ex) { /* safe */ }
			updateLabelButtons(cheaterBtn, legitBtn, clearBtn, "CHEATER");
			rebuildFightList();
		});
		legitBtn.addActionListener(e -> {
			try { plugin.setFightLabel(entry, "LEGIT"); } catch (Exception ex) { /* safe */ }
			updateLabelButtons(cheaterBtn, legitBtn, clearBtn, "LEGIT");
			rebuildFightList();
		});
		clearBtn.addActionListener(e -> {
			try { plugin.setFightLabel(entry, null); } catch (Exception ex) { /* safe */ }
			updateLabelButtons(cheaterBtn, legitBtn, clearBtn, null);
			rebuildFightList();
		});

		labelPanel.add(cheaterBtn);
		labelPanel.add(Box.createHorizontalStrut(4));
		labelPanel.add(legitBtn);
		labelPanel.add(Box.createHorizontalStrut(4));
		labelPanel.add(clearBtn);

		content.add(labelPanel);
		content.add(Box.createRigidArea(new Dimension(0, 12)));

		// Opponent analysis
		if (entry.getOpponentAnalysis() != null)
		{
			addAnalysisSection(content, "Opponent", entry.getOpponentAnalysis());
			content.add(Box.createRigidArea(new Dimension(0, 12)));
		}

		// Self analysis
		if (entry.getSelfAnalysis() != null)
		{
			addAnalysisSection(content, "Self", entry.getSelfAnalysis());
		}

		// Bottom spacer to push content up
		content.add(Box.createVerticalGlue());

		JScrollPane scrollPane = new JScrollPane(content);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		scrollPane.setBorder(null);
		scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);

		detailWrapper.add(scrollPane, BorderLayout.CENTER);
		detailWrapper.revalidate();
		detailWrapper.repaint();

		cardLayout.show(contentPanel, "detail");
	}

	private void addAnalysisSection(JPanel parent, String title, CheatAnalysis analysis)
	{
		boolean isSelf = "Self".equals(title);
		// Use a wrapper with GridBagLayout so the section gets full width
		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		section.setBorder(new EmptyBorder(8, 10, 8, 10));
		section.setAlignmentX(Component.LEFT_ALIGNMENT);
		section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

		// Title row with overall score
		JPanel headerRow = new JPanel();
		headerRow.setLayout(new BoxLayout(headerRow, BoxLayout.X_AXIS));
		headerRow.setOpaque(false);
		headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel titleLabel = new JLabel(title);
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
		headerRow.add(titleLabel);
		headerRow.add(Box.createHorizontalGlue());

		JLabel scoreLabel = new JLabel(String.format("%.0f - %s", analysis.getOverallScore(), analysis.getVerdict()));
		scoreLabel.setForeground(getScoreColor(analysis.getOverallScore()));
		scoreLabel.setFont(scoreLabel.getFont().deriveFont(Font.BOLD, 11f));
		headerRow.add(scoreLabel);

		section.add(headerRow);
		section.add(Box.createRigidArea(new Dimension(0, 8)));

		// Category score bars
		addScoreBar(section, "Gear Switch", analysis.getGearSwitchScore());
		addScoreBar(section, "Atk→Def", analysis.getAttackDefendScore());
		addScoreBar(section, "Pray React", analysis.getPrayerReactionScore());
		addScoreBar(section, "Pray Acc", analysis.getPrayerAccuracyScore());
		addScoreBar(section, "Consistency", analysis.getConsistencyScore());
		if (isSelf && analysis.getClickAnalysisScore() > 0)
		{
			addScoreBar(section, "Click Pattern", analysis.getClickAnalysisScore());
		}

		section.add(Box.createRigidArea(new Dimension(0, 8)));

		// Raw stats
		addKeyValue(section, "Max switch/tick", String.valueOf(analysis.getMaxGearSwitchesInTick()));
		addKeyValue(section, "Avg switch/tick", String.format("%.1f", analysis.getAvgGearSwitchesPerTick()));
		addKeyValue(section, "1t reactions",
			analysis.getZeroTickPrayerReactions() + "/" + analysis.getTotalPrayerOpportunities());
		addKeyValue(section, "Pray acc",
			String.format("%.1f%%", analysis.getPrayerAccuracyPct())
				+ " (" + analysis.getCorrectPrayerCount() + ")");
		addKeyValue(section, "Loadout perf", String.format("%.0f%%", analysis.getLoadoutPerfection()));
		if (analysis.getReactionAsymmetry() > 0)
		{
			addKeyValue(section, "React asymmetry", String.format("%.0f%%", analysis.getReactionAsymmetry() * 100));
		}

		// Flags
		List<String> flags = analysis.getFlags();
		if (flags != null && !flags.isEmpty())
		{
			section.add(Box.createRigidArea(new Dimension(0, 8)));

			JLabel flagsTitle = new JLabel("Flags:");
			flagsTitle.setForeground(COLOR_SUSPICIOUS);
			flagsTitle.setFont(flagsTitle.getFont().deriveFont(Font.BOLD, 11f));
			flagsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
			section.add(flagsTitle);

			for (String flag : flags)
			{
				JLabel flagLabel = new JLabel("<html><body style='width:180px'>\u2022 " + escapeHtml(flag) + "</body></html>");
				flagLabel.setForeground(new Color(255, 200, 100));
				flagLabel.setFont(flagLabel.getFont().deriveFont(10f));
				flagLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
				section.add(flagLabel);
			}
		}

		parent.add(section);
	}

	private void addScoreBar(JPanel parent, String label, double score)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

		JLabel nameLabel = new JLabel(label);
		nameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		nameLabel.setFont(nameLabel.getFont().deriveFont(10f));
		nameLabel.setMinimumSize(new Dimension(90, 16));
		nameLabel.setPreferredSize(new Dimension(90, 16));
		nameLabel.setMaximumSize(new Dimension(90, 16));
		row.add(nameLabel);
		row.add(Box.createHorizontalStrut(4));

		// Score bar
		JPanel barOuter = new JPanel() {
			@Override
			protected void paintComponent(Graphics g)
			{
				super.paintComponent(g);
				g.setColor(new Color(40, 40, 40));
				g.fillRect(0, 0, getWidth(), getHeight());
				if (score > 0)
				{
					g.setColor(getScoreColor(score));
					int barWidth = Math.max(1, (int) (getWidth() * score / 100.0));
					g.fillRect(0, 0, barWidth, getHeight());
				}
			}
		};
		barOuter.setMinimumSize(new Dimension(20, 12));
		barOuter.setPreferredSize(new Dimension(Short.MAX_VALUE, 12));
		barOuter.setMaximumSize(new Dimension(Short.MAX_VALUE, 12));
		barOuter.setOpaque(false);
		row.add(barOuter);
		row.add(Box.createHorizontalStrut(4));

		JLabel valLabel = new JLabel(String.format("%.0f", score));
		valLabel.setForeground(getScoreColor(score));
		valLabel.setFont(valLabel.getFont().deriveFont(10f));
		valLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		valLabel.setMinimumSize(new Dimension(24, 16));
		valLabel.setPreferredSize(new Dimension(24, 16));
		valLabel.setMaximumSize(new Dimension(24, 16));
		row.add(valLabel);

		parent.add(row);
	}

	private void addKeyValue(JPanel parent, String key, String value)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

		JLabel keyLabel = new JLabel(key);
		keyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		keyLabel.setFont(keyLabel.getFont().deriveFont(10f));
		row.add(keyLabel);

		row.add(Box.createHorizontalGlue());
		row.add(Box.createHorizontalStrut(6));

		JLabel valLabel = new JLabel(value);
		valLabel.setForeground(Color.WHITE);
		valLabel.setFont(valLabel.getFont().deriveFont(10f));
		// Prevent the value from expanding; let it shrink if needed
		valLabel.setMinimumSize(new Dimension(0, 16));
		row.add(valLabel);

		parent.add(row);
	}

	private void addLeftLabel(JPanel parent, String text, Color color, int style, float size)
	{
		JLabel label = new JLabel(text);
		label.setForeground(color);
		label.setFont(label.getFont().deriveFont(style, size));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		parent.add(label);
	}

	private void updateLabelButtons(JButton cheaterBtn, JButton legitBtn, JButton clearBtn, String label)
	{
		cheaterBtn.setBackground("CHEATER".equals(label) ? new Color(180, 40, 40) : null);
		cheaterBtn.setForeground("CHEATER".equals(label) ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		legitBtn.setBackground("LEGIT".equals(label) ? new Color(40, 140, 40) : null);
		legitBtn.setForeground("LEGIT".equals(label) ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		clearBtn.setEnabled(label != null);
	}

	private static Color getScoreColor(double score)
	{
		if (score >= 80) return COLOR_VERY_LIKELY;
		if (score >= 60) return COLOR_LIKELY;
		if (score >= 40) return COLOR_SUSPICIOUS;
		if (score >= 20) return COLOR_SLIGHT;
		return COLOR_LEGITIMATE;
	}

	private static String formatTime(long epochMs)
	{
		return DateTimeFormatter.ofPattern("HH:mm:ss")
			.withZone(ZoneId.systemDefault())
			.format(Instant.ofEpochMilli(epochMs));
	}

	private static String escapeHtml(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
